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

Finance consume `sales.receivable-payment.v1` y `expense.payable-payment.v1` con el grupo
`finance.cash-movements.v1`. Cada línea de cobro produce un ingreso con origen
`COBRO`, enlazado al pago, turno y cuenta de la empresa. El movimiento guarda
el medio, importe, moneda, fecha, factura, actor y traza. Los eventos V1
anteriores conservan sus identificadores de conciliación y dejan factura y
actor vacíos si el mensaje no los contiene.

Finance confirma el offset tras el insert en PostgreSQL. El replay deduplica
por `event_id` y por `(company_id, origin, source_id)`. Ante un fallo de
persistencia, el consumidor reintenta cada cinco segundos. Un mensaje
inválido también deja pendiente su partición hasta que se resuelva. Antes
de consumir, crea los topics según la [guía de Kafka](../development/kafka.md).

## Indicadores del turno activo

`GET /api/v1/cash-sessions/active/summary` requiere un JWT y `X-Company-Id`.
Finance resuelve el turno abierto de esa empresa con el operador del JWT.
Sales valida la pertenencia y el permiso `SALES_READ` con el mismo JWT y
proporciona el total de facturas cobrables de la empresa.

La respuesta incluye `shiftId`, `totalCollections` en DOP, `pendingInvoices`,
`netCash` en DOP y `otherMethods` con cobros por `CREDIT_CARD`, `DEBIT_CARD`,
`TRANSFER` y `CHECK`. Los cobros se calculan a partir de los ingresos confirmados y sus reversiones
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

## Proyecciones financieras

Finance expone una vista por empresa en `GET /api/v1/dashboard/projection`
y `GET /api/v1/reports/projection`. Ambos recursos requieren JWT,
`X-Company-Id` y acceso de lectura tanto en Sales como en Expense. Los
servicios propietarios vuelven a verificar los permisos en cada solicitud.
El [contrato OpenAPI](../../services/finance-service/openapi-financial-projections.yaml)
describe el corte y la estructura de las series.

Para el MVP, `POST /api/v1/reports/projection/rebuild` actualiza a demanda
la proyección documental. Obtiene por HTTP un snapshot versionado y consistente
dentro de cada fuente, y reemplaza la pareja en una sola escritura local.
Ejecuta esta operación para cargar una empresa por primera vez o reconciliar
su estado después de cambios en origen. Una respuesta tardía con un corte
anterior no sobrescribe una proyección más reciente. Si falla alguna fuente,
se conserva el snapshot anterior. Finance usa `FINANCE_SALES_ORIGIN` y
`FINANCE_EXPENSE_ORIGIN`; cada servicio consulta únicamente su base.

Las ventas, facturas y tipos de gasto se agrupan por separado para evitar
sumar una factura y la venta que la originó. Los saldos representan el estado
observado en cada fuente y su corte puede diferir. La actividad combina los
últimos documentos y movimientos confirmados. Los documentos con fecha
comercial sin hora se ordenan al inicio del día en Santo Domingo.

La caja se agrega desde movimientos locales DOP. El consumidor registra
cobros, pagos y reversiones V1 por el identificador del evento y del origen.
Una reversión de importe completo reduce el grupo del movimiento original
en el mes en que se hace efectiva. Para reconciliar la caja, vuelve a consumir
los eventos conservados por Kafka con sus identificadores originales. La
reconstrucción HTTP recalcula documentos y saldos, mientras que las series
de caja se calculan de nuevo al consultar el registro local.
