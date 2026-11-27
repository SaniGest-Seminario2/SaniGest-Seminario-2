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

## Indicadores del turno activo

`GET /api/v1/cash-sessions/active/summary` requiere un JWT y `X-Company-Id`.
Finance resuelve el turno abierto de esa empresa con el operador del JWT.
Sales valida la pertenencia y el permiso `SALES_READ` con el mismo JWT y
proporciona el total de facturas cobrables de la empresa.

La respuesta incluye `shiftId`, `totalCollections` en DOP, `pendingInvoices`,
`netCash` en DOP y `otherMethods` con cobros por `CREDIT_CARD`, `DEBIT_CARD`,
`TRANSFER` y `CHECK`. Los cobros se suman a partir de los ingresos confirmados
proyectados para el turno. El efectivo neto resta todas las salidas de efectivo
a las entradas de efectivo del turno. Un turno sin movimientos devuelve importes
en cero. Los cobros pueden tardar en aparecer mientras Kafka los proyecta.

Sin turno abierto devuelve `404`. Si Sales deniega la consulta se devuelve
`401` o `403` según corresponda. Si no se puede consultar el conteo devuelve
`503`.

Gateway usa `FINANCE_SERVICE_URL`. Finance usa `FINANCE_SALES_ORIGIN`
y la misma configuración `JWT_ISSUER`, `JWT_AUDIENCE` y
`JWT_PUBLIC_KEY_LOCATION` de los otros servicios. El Compose de QA
configura ambas rutas. Para probar el flujo, carga un turno abierto y sus
cuentas por método en Finance. S2-467 agregará la gestión de cuentas y
transferencias.

La hoja de ruta v0.8 también incluye adjuntos (S2-466), actividad y flujo
(S2-468) y detalle y exportación (S2-469). Las proyecciones MVP usan
Java/PostgreSQL (S2-387). S2-470 evalúa analítica predictiva.
