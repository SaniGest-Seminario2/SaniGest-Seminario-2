#!/usr/bin/env python3
"""Check Finance lag and quarantine, or replay one quarantined record."""
import argparse
import json
import sys

from confluent_kafka import Consumer, KafkaException, Producer, TopicPartition

SOURCES = ("sales.receivable-payment.v1", "expense.payable-payment.v1")
GROUP = "finance.cash-movements.v1"
DLQ = "finance.cash-movements.dlq.v1"
TIMEOUT = 10


def partitions(consumer, topic):
    metadata = consumer.list_topics(topic, timeout=TIMEOUT).topics[topic]
    if metadata.error:
        raise KafkaException(metadata.error)
    return [TopicPartition(topic, partition) for partition in metadata.partitions]


def check(consumer, max_lag):
    alert = False
    for topic in SOURCES:
        for state in consumer.committed(partitions(consumer, topic), timeout=TIMEOUT):
            if state.error:
                raise KafkaException(state.error)
            low, high = consumer.get_watermark_offsets(state, timeout=TIMEOUT)
            lag = high - max(state.offset, low)
            expired = 0 <= state.offset < low
            print(f"{topic}:{state.partition} lag={lag} expired_offset={expired}")
            alert |= lag > max_lag or expired

    for partition in partitions(consumer, DLQ):
        low, high = consumer.get_watermark_offsets(partition, timeout=TIMEOUT)
        print(f"{DLQ}:{partition.partition} retained_offsets={high - low}")
        alert |= high > low
    return int(alert)


def read_record(consumer, topic, partition, offset):
    consumer.assign([TopicPartition(topic, partition, offset)])
    record = consumer.poll(TIMEOUT)
    if (
        record is None
        or record.error()
        or (record.topic(), record.partition(), record.offset()) != (topic, partition, offset)
    ):
        raise ValueError(f"Exact record unavailable at {topic}:{partition}:{offset}")
    return record


def replay(consumer, config, args):
    entry = read_record(consumer, DLQ, args.partition, args.offset)
    metadata = json.loads(entry.value())
    topic, partition, offset = (
        metadata["topic"], metadata["partition"], metadata["offset"]
    )
    if (
        topic not in SOURCES
        or type(partition) is not int or partition < 0
        or type(offset) is not int or offset < 0
        or entry.key() != f"{topic}:{partition}:{offset}".encode()
    ):
        raise ValueError("Invalid DLQ coordinates")

    record = read_record(consumer, topic, partition, offset)
    producer = Producer({**config, "enable.idempotence": True, "message.timeout.ms": 10000})
    delivery = []
    producer.produce(
        topic, partition=partition, key=record.key(), value=record.value(),
        headers=record.headers(), on_delivery=lambda error, message: delivery.append(error),
    )
    if producer.flush(12) or delivery != [None]:
        raise ValueError("Replay delivery unconfirmed. Check Finance before retrying.")
    print(f"Replayed {topic}:{partition}:{offset}")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=argparse.FileType("r"), required=True,
                        help="JSON Kafka client configuration")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("check").add_argument("--max-lag", type=int, default=100)
    replay_args = commands.add_parser("replay")
    replay_args.add_argument("partition", type=int, help="DLQ partition")
    replay_args.add_argument("offset", type=int, help="DLQ offset")
    replay_args.add_argument("--confirm", action="store_true", required=True)
    args = parser.parse_args()
    with args.config:
        config = json.load(args.config)
    consumer = Consumer({
        **config, "group.id": GROUP, "enable.auto.commit": False,
        "auto.offset.reset": "error", "allow.auto.create.topics": False,
    })
    try:
        if args.command == "check":
            if args.max_lag < 0:
                parser.error("--max-lag must be nonnegative")
            return check(consumer, args.max_lag)
        if args.partition < 0 or args.offset < 0:
            parser.error("DLQ coordinates must be nonnegative")
        return replay(consumer, config, args)
    finally:
        consumer.close()


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (KafkaException, OSError, ValueError, KeyError, TypeError) as error:
        print(error, file=sys.stderr)
        sys.exit(2)
