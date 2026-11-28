# Operación de eventos de pagos

## Aprovisionamiento y alertas

Crea `finance.cash-movements.dlq.v1` antes de iniciar Finance. La
[configuración local](../development/kafka.md#topics-y-particiones) usa una
partición y siete días de retención. Configura la retención de los topics de
origen para cubrir el plazo de investigación y recuperación. Da a Finance
permiso de escritura en la DLQ y reserva la escritura en los topics de origen
para los productores y el rol de recuperación.

Finance reintenta errores transitorios de acceso a datos hasta tres veces,
con esperas de 1, 2 y 4 segundos. Envía fallos definitivos directamente a
cuarentena. El offset puede avanzar cuando Kafka confirma la escritura en la
DLQ. Si falla esa escritura, el offset sigue pendiente. La DLQ guarda topic,
partición, offset y un código de razón. Puede haber varias entradas para las
mismas coordenadas cuando el proceso se interrumpe antes de confirmar el offset.

Instala `confluent-kafka` en el entorno Python del operador. Usa un archivo JSON
de configuración de cliente de Kafka, por ejemplo
`{"bootstrap.servers":"localhost:9092"}` para desarrollo local. Para monitoreo,
ejecuta cada minuto con una identidad de lectura de offsets y metadatos del grupo
`finance.cash-movements.v1` y los tres topics.

```sh
python3 scripts/ops/payment-kafka.py --config kafka-monitor.json check --max-lag 100
```

Conecta el código de salida al monitor de tareas. El código `1` alerta por lag
superior al umbral en alguna partición, offsets confirmados vencidos o entradas
retenidas en la DLQ. Agrupa la alerta de DLQ por incidente hasta que expiren
las entradas investigadas. El código `2` indica que falló la consulta.
En el colector de logs de Finance, alerta cuando aparezca
`PAYMENT_PROJECTION_REPEATED_FAILURE` (tercer intento fallido de un evento)
o `PAYMENT_PROJECTION_DLQ_UNAVAILABLE` (escritura de cuarentena fallida).
Los logs de Sales y Expense con `deferred for 300s` indican que el outbox agotó
otro ciclo de publicación.

## Investigación y replay

1. Ubica la entrada de DLQ y registra sus coordenadas, su razón y las
   coordenadas originales. Usa acceso restringido al topic de origen para
   inspeccionar el evento si es necesario.
2. Corrige la causa. Para una reversión, verifica que el movimiento original
   exista antes de reenviarla. Si el registro original expiró, recupera el
   evento exacto del outbox conservando su clave e identificadores. Si el
   evento es inválido, investiga el productor antes de intentar un replay.
3. Ejecuta un replay por entrada con una identidad autorizada para leer la DLQ
   y leer y publicar en el topic original. Este comando usa la partición y el
   offset **de la DLQ**.

   ```sh
   python3 scripts/ops/payment-kafka.py --config kafka-replay.json replay 0 42 --confirm
   ```

   La herramienta verifica que exista el registro exacto al que apunta la
   entrada y lo agrega al final de su partición original con la misma clave,
   contenido y headers. Finance conserva su deduplicación por `eventId` y
   `companyId + origin + sourceId`. Verifica la proyección y revisa si el
   replay produjo una nueva entrada de DLQ. Si no se confirmó la entrega,
   comprueba el movimiento antes de repetir la operación.

## Reconciliación

Configura conexiones libpq de solo lectura llamadas `sanigest_sales`,
`sanigest_expense` y `sanigest_finance` en `pg_service.conf`. Ejecuta con
`psql` disponible y una empresa explícita.

```sh
python3 scripts/ops/reconcile-payments.py --company "$COMPANY_ID" --grace-minutes 15
```

La comparación usa las líneas de cobros confirmados, los abonos confirmados y
las reversiones del outbox. Emite `MISSING_MOVEMENT` para fuentes fuera del
período de gracia y `ORPHAN_MOVEMENT` para movimientos sin fuente. Cada fila
incluye empresa, origen y fuente. El código `1` indica discrepancias y `2`
un fallo de consulta. Las tres bases se leen por separado. Repite el chequeo
si había una proyección en tránsito.

Para un movimiento faltante, revisa primero el outbox y luego el lag y la DLQ.
Para un huérfano, verifica el identificador de la línea de cobro o del abono
y el historial de migración antes de corregir los datos.
