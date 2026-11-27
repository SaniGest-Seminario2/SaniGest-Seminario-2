# Finance Service

S2-349 incorpora Finance al reactor Maven y a Docker Compose. Finance posee
turnos, cuentas y movimientos en `sanigest_finance`. Los identificadores son
UUID y las relaciones entre turno, cuenta y movimiento incluyen la empresa.

Sales consulta por GET
`/internal/companies/{companyId}/operators/{operatorId}/active-shift`
antes de registrar un cobro nuevo. La lectura devuelve el turno abierto y
el mapa de método a cuenta. El operador proviene de la sesión autenticada
en Sales. Un turno ausente o un método sin cuenta produce conflicto. Una
falla de comunicación produce 503. Sales usa 500 ms para establecer conexión
y 1 s para la lectura. La consulta es una foto del turno al admitir el cobro.
Sales confirma el cobro, el saldo y los eventos del outbox en una transacción.

En Compose, configura el mismo `FINANCE_ADMISSION_TOKEN` secreto para ambos
servicios y activa `FINANCE_ADMISSION_ENABLED=true`. Fuera de Compose, Sales
usa `SANIGEST_FINANCE_ENABLED`, `SANIGEST_FINANCE_ORIGIN` y
`SANIGEST_FINANCE_ADMISSION_TOKEN`. Sales requiere HTTPS fuera de los
perfiles `local` y `test`. La consulta interna queda dentro de la red de
servicios de Compose.

Finance consume `sales.receivable-payment.v1` con el grupo
`finance.cash-movements.v1`. Cada línea de cobro produce un ingreso con origen
`COBRO`, enlazado al pago, turno y cuenta de la empresa. El movimiento guarda
el medio, importe, moneda, fecha, factura, actor y traza. Los eventos V1
anteriores conservan sus identificadores de conciliación y dejan factura y
actor vacíos si el mensaje no los contiene.

Finance confirma el offset tras el insert en PostgreSQL. El replay deduplica
por `event_id` y por `(company_id, origin, source_id)`. Ante un fallo de
persistencia, el consumidor reintenta cada cinco segundos. Un mensaje
inválido también deja pendiente su partición hasta que se resuelva. Antes
de consumir, crea el topic según la [guía de Kafka](../development/kafka.md).

S2-464 implementará la gestión de turnos y arqueo, y S2-467 agregará la gestión
de cuentas y transferencias. Para probar la admisión antes de esos flujos,
carga un turno abierto y sus cuentas por método en la base de Finance.

La hoja de ruta v0.8 también incluye adjuntos (S2-466), actividad y flujo
(S2-468) y detalle y exportación (S2-469). Las proyecciones MVP usan
Java/PostgreSQL (S2-387). S2-470 evalúa analítica predictiva.
