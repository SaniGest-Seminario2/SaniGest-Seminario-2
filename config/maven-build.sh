#!/bin/sh
# Retry transient repository failures; preserve the final Maven exit status.
attempt=1
while :; do
    mvn -B -U "$@" && exit 0
    result=$?
    [ "$attempt" -ge 3 ] && exit "$result"
    sleep "$((attempt * 10))"
    attempt=$((attempt + 1))
done
