#!/bin/sh
# Resolver can retry an HTTP transfer without restarting the Maven lifecycle.
# Only connection interruptions supported by HTTP Client and HTTP 429/503 are retried.
printf '%s\n' 'Maven allows up to four retries for eligible repository transfers. HTTP 429/503 retries log “Wait for” and the delay in milliseconds.'
exec mvn -B -ntp \
    -Daether.connector.http.retryHandler.count=4 \
    -Daether.connector.http.retryHandler.serviceUnavailable=429,503 \
    -Dorg.slf4j.simpleLogger.log.org.apache.http.impl.execchain.ServiceUnavailableRetryExec=trace \
    "$@"
