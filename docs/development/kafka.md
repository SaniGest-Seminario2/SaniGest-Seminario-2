# Kafka en desarrollo

```sh
docker compose --profile infra up -d --wait kafka
```

Compose ejecuta un broker KRaft. Usa `localhost:${KAFKA_PORT:-9092}` desde el
host y `kafka:29092` desde contenedores. El health check consulta el broker
por el listener interno. El volumen `kafka-data` conserva los datos y los
offsets entre reinicios. La creación automática de topics está deshabilitada.

## Topics y particiones

| Topic | Productor → consumidor | Clave | Retención local |
| --- | --- | --- | --- |
| `sales.receivable-payment.v1` | Sales → Finance | `companyId` | 7 días |
| `expense.payable-payment.v1` | Expense → Finance | `companyId` | 7 días |
| `qa.smoke.v1` | CLI del smoke test | `qa-company` | 1 hora |

Los topics financieros se aprovisionan explícitamente al integrar los flujos.
Para desarrollo, crear cada topic con una partición, factor de replicación 1,
`cleanup.policy=delete` y `retention.ms=604800000`.

Los nombres siguen `<dominio>.<flujo>.v<versión mayor>`. Los cambios
compatibles conservan el topic. Un cambio incompatible requiere una nueva
versión del topic y una migración coordinada de los consumidores. Los cobros
y pagos confirmados usan su flujo respectivo. Cuando existan reversiones,
comparten el topic y la clave del pago original. Kafka preserva el orden
de publicación dentro de una partición de un topic. El publicador del outbox
debe enviar los eventos en el orden requerido para cada clave. Mantén una
clave estable y planifica cualquier cambio en el número de particiones si
el flujo depende de ese orden.

## Grupos, offsets y diagnóstico

Finance usa el grupo estable `finance.cash-movements.v1` para ambos topics.
Las réplicas comparten ese grupo. Otro consumidor lógico usa su propio grupo
para mantener sus offsets independientes. El consumidor confirma el offset
después de persistir el efecto idempotente. Un grupo nuevo de pruebas puede
leer desde el inicio de los datos retenidos. Para un replay, detén el grupo,
revisa los offsets y conserva el identificador original de cada evento.

```sh
docker compose logs --tail 100 kafka
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:29092 --describe
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:29092 --describe --group finance.cash-movements.v1
sh infrastructure/qa/smoke-infrastructure.sh
```

La descripción del grupo muestra el offset confirmado, el fin del log y el
lag por partición. El smoke test verifica la salud del broker y compara la
clave y el payload del mensaje recibido con los valores publicados.

La entrega es al menos una vez. El productor persiste el cambio de negocio
y el registro del outbox en la misma transacción PostgreSQL. Finance deduplica
por `eventId` y protege el movimiento con `(companyId, origin, sourceId)`.
PostgreSQL y Kafka no ofrecen una garantía conjunta de exactly-once. Los
[contratos Java](../../libraries/common-events/README.md) corresponden a S2-384 y la política de transporte compartida
para clientes, reintentos, DLQ y headers corresponde a S2-437.

- [ADR de propiedad de datos y comunicación](../adr/ADR-001-propiedad-datos-comunicacion-servicios.md)
- [S2-384](https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-384) · [S2-437](https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-437)

## Outbox de pagos confirmados

Sales y Expense tienen su propia tabla `payment_outbox`. Los casos de uso de
confirmación de S2-339 y S2-347 deben llamar a `PaymentOutbox.enqueue(companyId, event)`
dentro de la misma transacción local que persiste el pago. La llamada exige una
transacción activa. Cada línea confirmada tiene su propio `sourceId` y
`eventId`, y comparte el `paymentId` del pago. Las rutas actuales de creación
de ventas y gastos todavía no registran estos pagos. En un reintento de la
operación, el caso de uso debe resolver la idempotencia antes de volver a encolar.

El publicador toma una fila por transacción con `FOR NO KEY UPDATE SKIP LOCKED` y
registra `published_at` después del acuse de Kafka. Una reversión publicada como
evento compensatorio espera a que se publique su original de la misma empresa.
Ante fallo, los reintentos esperan 2, 4, 8 y 16 segundos. Tras el quinto fallo,
la fila descansa cinco minutos antes de un nuevo ciclo. El evento queda
pendiente en PostgreSQL durante el fallo y sobrevive a reinicios.

La entrega es **al menos una vez**. Si falla el proceso tras el acuse de Kafka
y antes del commit, se reenvía con los mismos identificadores. Finance debe
deduplicar por `eventId` y proteger el efecto por
`(companyId, origin, sourceId)`. Los eventos publicados se conservan para
mantener la referencia de las reversiones.

El publicador está habilitado por defecto. Para suspenderlo sin impedir que se
encolen pagos, configura `SANIGEST_PAYMENT_OUTBOX_RELAY_ENABLED=false`.
`SPRING_KAFKA_BOOTSTRAP_SERVERS` usa `kafka:29092` en Compose y
`localhost:9092` por defecto fuera de Compose.
