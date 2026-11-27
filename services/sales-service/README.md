# Sales Service

Sales es dueño de las ventas y sus líneas en PostgreSQL. S2-331 entrega el
modelo, la persistencia y los endpoints de salud. S2-332 incorpora el registro
HTTP de ventas. S2-333 incorpora el listado paginado. S2-334 incorpora el detalle
y la cancelación.
Consulta el [contrato objetivo v0.8](../../docs/SaniGest_T1001_Contrato_Funcional_v0.8.md)
y el [ADR-001](../../docs/adr/ADR-001-propiedad-datos-comunicacion-servicios.md).

## Arquitectura y datos

- `domain` conserva los agregados y reglas de venta en Java.
- `application.port.out.persistence` define la escritura atómica del agregado
  y las consultas de resumen y detalle por empresa.
- `infrastructure.persistence.jpa` mapea el agregado a sus tablas privadas.
  `infrastructure.security` configura la validación de JWT y el acceso a salud.

Sales usa la base y el rol `sanigest_sales`, provisionados por el bootstrap de
PostgreSQL. Flyway crea el esquema `sales` y administra las migraciones.
Hibernate valida el esquema al arrancar y Open Session in View está
deshabilitado. La única FK conecta una línea con su venta.
Los UUID externos y los nombres, descripciones y precios efectivos se guardan
como snapshots de la operación.

La fecha comercial es `LocalDate` y se guarda como `DATE`. La modalidad es
`CASH` o `CREDIT`. El ciclo de vida inicial usa `REGISTERED` y `CANCELLED`.
Los estados de cobro del contrato se derivarán de cobros verificados. La
cancelación registra actor, fecha y motivo en la venta.

Los precios, subtotales y totales son `BigDecimal` en DOP con dos decimales,
hasta `99999999999999999.99`. La cantidad positiva admite hasta tres decimales
y debe ser inferior a `1000000000000`. El subtotal es el producto exacto
de precio y cantidad. Se rechazan las fracciones de centavo y los importes fuera
de rango, con `RoundingMode.UNNECESSARY` al normalizar. PostgreSQL usa
`NUMERIC` con controles de escala y rango para rechazar entradas inválidas
antes de que puedan redondearse. La suma de líneas se valida en el agregado,
que el adaptador inserta en una única transacción local.

## Arranque local

Configura la contraseña en `.env` y genera la clave pública según la
[configuración JWT](../../docs/seguridad-s2-317.md). Desde la raíz del repo:

```sh
docker compose --profile apps up -d --build --wait sales-service
curl -fsS http://localhost:8084/actuator/health
```

Para ejecutar el JAR localmente, levanta PostgreSQL con
`docker compose --profile infra up -d --wait postgres` y exporta las variables
necesarias antes de ejecutar:

```sh
mvn -B -pl services/sales-service -am package
java -jar services/sales-service/target/sales-service-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

| Variable | Configuración |
| --- | --- |
| `SALES_DB_HOST` | Obligatoria en el perfil base. El perfil `local` usa `localhost` por defecto. |
| `SALES_DB_PORT` | Puerto PostgreSQL. El perfil `local` admite `POSTGRES_PORT` como alternativa. Predeterminado `5432`. |
| `SALES_DB_PASSWORD` | Contraseña del rol `sanigest_sales`. |
| `SALES_SERVICE_PORT` | Puerto local o publicado por Compose. Predeterminado `8084`. |
| `SERVER_PORT` | Sobrescribe el puerto del proceso Spring Boot. |
| `JWT_ISSUER`, `JWT_AUDIENCE` | Obligatorios en el perfil base. En `local` se usan `sanigest` y `sanigest-api` por defecto. |
| `JWT_PUBLIC_KEY_LOCATION` | Ubicación de la clave pública RSA en PEM, por ejemplo `file:/ruta/jwt-public.pem`. |

La salud y sus sondas `liveness` y `readiness` son públicas. La respuesta muestra
el estado y, en la ruta raíz, los nombres de las sondas. Readiness comprueba
PostgreSQL. Las rutas de ventas exigen JWT. Las demás rutas
funcionales se deniegan por defecto. La seguridad reutiliza `common-security` para verificar firma, issuer,
audience y expiración.

## Pruebas

```sh
mvn -B -pl services/sales-service -am test
mvn -B -pl services/sales-service -am verify
mvn -B -pl services/sales-service -am -Psmoke verify
mvn -B -pl services/sales-service -am -Pregression verify
```

El perfil de pruebas usa PostgreSQL 17 en Testcontainers y un puerto HTTP
dinámico. V1 a V5 se validan desde una base limpia y se ejecuta Flyway por segunda
vez para comprobar el historial, checksum y ausencia de migraciones pendientes.

Finance recibirá eventos financieros al incorporar cobros, mediante outbox y
Kafka conforme al ADR-001.

## Autorización empresarial

`SalesAuthorization` resuelve el UUID de la empresa autorizada a partir de una
membresía vigente o una delegación explícita. El adaptador
`CompanySalesAuthorization` consulta Company para el módulo `SALES` mediante
`common-company-access`. Los roles y concesiones se obtienen de Company en cada
operación. `X-Company-Id` selecciona la empresa que Company verifica contra la
membresía vigente del actor.

| Rol o contexto | SALES_READ | SALES_WRITE | SALES_CANCEL |
| --- | --- | --- | --- |
| OWNER | Sí | Sí | Sí |
| ADMIN | Sí | Sí | Sí |
| OPERATOR | Sí | Con concesión vigente SALES_WRITE | Denegado |
| VIEWER (READ_ONLY en Jira) | Sí | Denegado | Denegado |
| ADVISOR | Delegación vigente SALES/READ | Delegación vigente SALES/WRITE | Denegado |

- `SALES_READ` usa `READ` y cubre listado, detalle y líneas. `SALES_WRITE`
  usa `WRITE` y cubre registro.
- `SALES_CANCEL` consulta la membresía con `WRITE` y exige el rol vigente
  OWNER o ADMIN. El alcance genérico WRITE y las concesiones de OPERATOR
  carecen de efecto para cancelar.
- ADVISOR usa exclusivamente el método delegado con un UUID explícito. Company
  verifica en Identity la elegibilidad actual y comprueba empresa activa,
  alcance exacto y revocación de la concesión. El modelo vigente de Company
  mantiene la concesión hasta su revocación y carece de una fecha de expiración
  por concesión. Sales exige un JWT vigente en cada resolución. Los flujos
  públicos de Sales aún carecen de selección delegada y de auditoría de operaciones Sales.
  El cliente actual de Catalog resuelve membresía regular, por lo que un
  futuro registro delegado también requiere un contrato de Catalog compatible.
- Una revocación confirmada surte efecto en la siguiente consulta a Company.
  Una operación ya autorizada puede seguir en curso. La integración
  deshabilitada, un timeout o una respuesta inválida producen
  `SalesAuthorizationUnavailable` (503). Una denegación de Company o de la
  política produce `SalesAccessForbidden` (403). El filtro distingue sesión
  ausente o inválida (401) y el advice traduce errores de negocio al formato
  Problem Details.

### Configuración de Company

Registra la clave pública específica de Sales en Company bajo
`sanigest.company-access.callers.sales`, con `issuer` y `public-key`.
Monta en Sales su clave privada RSA PKCS#8. Habilitar el cliente construye
la configuración local y difiere el tráfico remoto hasta la primera decisión.

| Variable | Valor |
| --- | --- |
| `SALES_COMPANY_ACCESS_ENABLED` | `false` por defecto. |
| `SALES_COMPANY_ACCESS_ORIGIN` | Origen privado de Company. HTTPS, con HTTP admitido en perfiles `local` y `test`. |
| `SALES_COMPANY_ACCESS_ISSUER` | `sales-company-access` por defecto. Debe coincidir con el emisor registrado para SALES. |
| `SALES_COMPANY_ACCESS_PRIVATE_KEY_LOCATION` | Ubicación `file:` de la clave privada exclusiva de Sales. |
| `SALES_COMPANY_ACCESS_CONNECT_TIMEOUT` | `500ms` por defecto. |
| `SALES_COMPANY_ACCESS_RESPONSE_TIMEOUT` | `2s` por defecto. |

### Rutas y permisos

| Ruta | Permiso | Ticket |
| --- | --- | --- |
| POST /api/v1/sales (legado, desactivado por defecto) | SALES_WRITE | S2-332 |
| GET /api/v1/sales | SALES_READ | S2-333 |
| GET /api/v1/sales/{id} | SALES_READ | S2-334 |
| POST /api/v1/sales/{id}/cancel | SALES_CANCEL | S2-334 |
| POST /api/v1/sales/{saleId}/invoices | SALES_WRITE | S2-335 |
| POST /api/v1/invoices | SALES_WRITE | S2-462 |
| GET /api/v1/invoices, /all, /collectible, /collectible/all y /{id} | SALES_READ | S2-462 |
| POST /api/v1/invoices/{id}/cancel | SALES_CANCEL | S2-462 |

Sales expone salud, registro, listado, detalle, cancelación y emisión manual de facturas internas.
Al implementar cada ruta, exigir autenticación en el filtro y en el método
del bean Spring que sirve de entrada al caso de uso, con
`@PreAuthorize("isAuthenticated()")`. La regla ArchUnit vigente mantiene
`application` libre de dependencias de Spring. Ubicar la anotación en el
adaptador de entrada que invoca al caso de uso. El caso de uso debe obtener el
UUID mediante el puerto una vez por operación, antes de tocar Catalog o la persistencia,
y utilizar el UUID devuelto para crear o buscar por empresa. Para detalle y
cancelación la consulta debe incluir empresa e identificador. Un objeto
inexistente o ajeno se traduce a 404. Validar parámetros de entrada
en la frontera HTTP para devolver 400 cuando corresponda. Invocar el bean
a través del proxy Spring para activar Method Security.

`CompanySalesAuthorizationTest` prueba la matriz.
`SalesAuthorizationIntegrationTest` arranca Spring, usa tokens firmados con
claves temporales y WireMock para verificar el protocolo, los fallos, las
revocaciones, la invocación mediante proxy y la denegación de rutas. La
regresión con PostgreSQL verifica la consulta JPA por empresa e identificador.
El recorrido HTTP cubre registro, listado, detalle y cancelación.

## Integración con Catalog

`application.port.out.integration.CatalogClient` entrega snapshots de clientes
y artículos elegibles para una venta. El adaptador de
`infrastructure.integration.catalog` ejecuta dos GET mediante una interfaz
declarativa de Spring y `RestClient`.

| Ruta en Catalog | Snapshot de Sales |
| --- | --- |
| `GET /api/v1/clients/{id}` | UUID, nombre y estado `ACTIVE`. |
| `GET /api/v1/catalog-items/{id}` | UUID, nombre, tipo `PRODUCT` o `SERVICE`, descripción nullable, precio exacto con escala 2, moneda `DOP` y estado activo. |

Catalog añade `availableForSale`. Sales rechaza un valor explícito `false` y
conserva la compatibilidad histórica cuando el campo está ausente. El precio
neto sigue siendo un número JSON y se valida con el rango y escala históricos
antes de crear el snapshot. S2-461 coordinará el consumo de cuatro decimales,
las tasas vigentes y el redondeo definitivo por línea.


Los DTO de transporte son privados de la integración. El cliente valida
identificadores, estructura, estados e importes antes de construir el snapshot.
S2-332 consumirá estos valores para registrar la venta y persistir el importe y
los textos vigentes al momento de vender. Las ventas históricas se consultan
desde Sales.

| Variable | Configuración |
| --- | --- |
| `SALES_CATALOG_ORIGIN` | Origen dedicado de Catalog, por ejemplo `https://catalog.internal`. El perfil `local` y las pruebas admiten HTTP. Compose apunta a `http://catalog-service:8080` por defecto. Si está vacío, las consultas fallan con `NOT_CONFIGURED`. |
| `SALES_CATALOG_CONNECT_TIMEOUT` | Timeout de conexión. Predeterminado `500ms`. |
| `SALES_CATALOG_RESPONSE_TIMEOUT` | Timeout de respuesta. Predeterminado `2s`. |

Sales propaga el JWT de sesión validado por Spring Security al origen
configurado. Sales y Catalog usan la firma, el emisor y la audiencia del
contrato compartido. El cliente bloquea redirecciones y carece de reintentos
configurados. Los GET de detalle requieren `CATALOG_READ`. Catalog resuelve
la membresía vigente contra Company en cada consulta y busca por la empresa
autorizada y el UUID. Habilita la integración de Catalog con Company para
autorizar estas consultas según su [configuración](../../libraries/common-company-access/README.md). Sales envía
la empresa autorizada en `X-Company-Id` y `expectedCompanyId`, y valida el tenant
de la respuesta. Si el actor carece de `CATALOG_READ`, se rechaza el registro.
Consulta el [contrato de rutas](../../docs/development/company-context.md).
Micrometer observa las llamadas HTTP y se envían `X-Trace-Id` y `traceparent`
para correlación. Las excepciones omiten cuerpos de respuesta y credenciales.

| Fallo | Traducción en Application |
| --- | --- |
| Sesión ausente o vencida, 401, 403 | `CatalogAccessDeniedException` con motivos distintos. |
| 400, 404, recurso inactivo o JSON inválido | `CatalogResourceInvalidException`. Un 404 puede indicar un recurso inexistente o fuera de la empresa autorizada. |
| 429, 5xx, fallo de red o timeout | `CatalogUnavailableException`. |

Catalog puede estar indisponible durante el arranque de Sales. La salud de Sales
se comprueba con sus propias dependencias y una consulta remota fallida termina
sin snapshot. La prueba transaccional de registro corresponde a S2-332.

```sh
mvn -B -pl services/sales-service -am -Dtest=CatalogHttpClientTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -pl services/sales-service,services/catalog-service -am verify
```

WireMock cubre el tráfico saliente, contratos y fallos de Catalog. La
regresión de Catalog usa su configuración real de Spring Security, JWT
firmados, PostgreSQL y la autoridad Company simulada para comprobar
aislamiento y revocación en los dos GET de detalle.

### Registro de ventas (S2-332)

`POST /api/v1/sales` recibe `clientId`, `lines` (entre 1 y 100 entradas con
`catalogItemId` y `quantity`), `mode` (`CASH` o `CREDIT`) y `saleDate` opcional.
Las ventas CREDIT nuevas requieren `dueDate` en formato `YYYY-MM-DD`, igual o
posterior a la fecha efectiva de venta. En CASH se debe omitir o enviar nulo.
La fecha predeterminada usa `America/Santo_Domingo`. Ambos modos comienzan en
`REGISTERED`. Los importes DOP se devuelven como cadenas decimales.

Sales calcula y conserva los precios y subtotales históricos. La cantidad
admite hasta tres decimales significativos. Si el producto cantidad × precio
excede el rango permitido o requiere más de dos decimales significativos,
la venta se rechaza con 422. El mismo rango monetario se aplica al total.

Sales transmite el JWT del actor a Catalog junto con `expectedCompanyId`,
obtenido de su autorización de escritura. Catalog compara ese UUID con su
propia membresía autorizada antes de buscar. Sales exige la confirmación
`X-Catalog-Company-Id` en cada respuesta. Un cambio de contexto devuelve 403.
Los recursos ausentes y los de otra empresa bajo un contexto válido comparten
el 404 de Catalog. Desplegar el contrato de Catalog antes de habilitar Sales.

El código sigue `V-AAAA-NNN` con un mínimo de tres dígitos y una secuencia por
empresa y año de venta. La asignación y la escritura de venta y líneas se
confirman en la misma transacción de Sales. V2 admite ventas anteriores sin
código y preserva sus datos.

### Listado de ventas (S2-333)

`GET /api/v1/sales` exige un JWT y una membresía con `SALES_READ`. La empresa
se resuelve en Company. La consulta usa el nombre histórico del cliente
guardado en Sales.

| Parámetro opcional | Filtro |
| --- | --- |
| `search` | Subcadena literal del nombre, sin distinguir mayúsculas. Se recortan los espacios exteriores. Máximo 250 caracteres UTF-16. |
| `status` | `REGISTERED` o `CANCELLED`, sin distinguir mayúsculas. |
| `from`, `to` | Límites inclusivos e independientes en `yyyy-MM-dd`, del año 0001 al 9999. |
| `page` | Índice desde cero. Predeterminado `0`. |
| `size` | Predeterminado `20`. Máximo `100`, configurable con `sanigest.sales.max-page-size`. |

La respuesta incluye `content`, `page`, `size`, `totalElements` y
`totalPages`. El orden es por fecha e identificador descendentes. Un
desplazamiento superior a 2147483647 o filtros inválidos devuelven 400.
Una página válida sin filas devuelve 200 con `content: []`.
El conteo y la página comparten una instantánea de PostgreSQL por petición.
Cada elemento incluye `id`, `code`, `client` con `id` y `name`,
`saleDate`, `mode`, `status`, `total` como cadena decimal y
`currency` con valor `DOP`. Las ventas anteriores a la asignación de
códigos pueden tener `code: null`.

El filtro por `PENDING`, `PARTIALLY_PAID` y `PAID` queda pendiente de
los cobros verificados y de su proyección de estado. La modalidad solo
identifica si la venta se registró como `CASH` o `CREDIT`.

### Detalle y cancelación (S2-334)

`GET /api/v1/sales/{id}` devuelve los campos del registro con el cliente
histórico, las líneas en el orden guardado y los importes exactos en DOP.
Las líneas incluyen `id`, `catalogItemId`, `itemName`, `itemDescription`,
`unitPrice`, `quantity` y `subtotal`. El campo `cancellation` aparece
cuando hay metadatos persistidos. La consulta también incluye `invoiceId`,
`invoiceNumber`, `dueDate`, `paymentStatus`, `outstandingBalance` y
`collectedAmount` cuando existe el documento u obligación correspondiente.
Los listados incluyen `invoiceNumber` y `paymentStatus`. El detalle de
obligación conserva `balance` y agrega `outstandingBalance`, `saleCode`,
`invoiceId` e `invoiceNumber`. Para una obligación CANCELLED, `balance` conserva el saldo histórico,
`outstandingBalance` devuelve `"0.00"` y `collectedAmount` se calcula
a partir del balance histórico.

`POST /api/v1/sales/{id}/cancel` recibe un UUID y un objeto JSON como
`{"reason":"Venta registrada por duplicado"}`. El motivo, tras aplicar
`strip()`, debe contener entre 1 y 500 unidades UTF-16. Una petición válida
devuelve 200 con el detalle para una cancelación nueva o repetida. La primera
cancelación conserva sus valores `cancelledBy`, `cancelledAt` (UTC) y
`cancelReason`. La operación usa como clave natural la empresa autorizada y
el UUID de la venta, sin header de idempotencia.

El adaptador PostgreSQL adquiere `FOR UPDATE` sobre la fila de la venta y carga
el agregado dentro de la misma transacción. La actualización exige estado
`REGISTERED` y verifica que cambió exactamente una fila. Una actualización
inesperadamente rechazada devuelve 409. V4 preserva las filas históricas
`CANCELLED` que carecían de metadatos y exige los tres campos para nuevas
cancelaciones.

#### Contrato con cuentas por cobrar y abonos

El esquema actual de Sales permite registrar ventas y carece de operaciones
para persistir cobros o reversiones. El detalle omite `payments`. Ambos modos
pueden cancelarse cuando la venta carece de factura vigente y se cumplen las reglas de cuentas por cobrar.

### Cuenta por cobrar de una venta (S2-337)

Una venta CREDIT nueva crea una cuenta DOP con estado `OPEN`, saldo igual al
total positivo de la venta y snapshot del cliente. El UUID de la cuenta es
el mismo de la venta. `GET /api/v1/receivables/{id}` requiere JWT y
`SALES_READ` en la empresa resuelta por Sales. Una cuenta ausente o ajena
devuelve 404.

El detalle devuelve `id`, `saleId`, `client {id, name}`, `origin`,
`totalAmount`, `balance`, `currency`, `dueDate` y `status`. Los importes
son cadenas con dos decimales. CASH no genera cuenta ni implica cobro
verificado. El registro de venta y cuenta no genera cobros, facturas,
movimientos de caja ni eventos.

La migración V5 conserva las ventas previas. Las CREDIT históricas no
tenían un vencimiento persistido y quedan sin cuenta, incluso cuando
se reproduce su respuesta idempotente. Una regularización de cartera
histórica requiere datos de vencimiento verificados y una decisión de
negocio. El origen `OPENING_BALANCE` queda reservado para S2-385 y admite
una obligación sin venta asociada.

Al cancelar, Sales conserva el importe y saldo de la cuenta como historial,
marca la cuenta `CANCELLED` y la excluye del saldo exigible. El estado
`OVERDUE` se deriva al consultar una obligación exigible cuyo vencimiento
ya pasó. La cancelación conserva actor, fecha y motivo en la venta.

Para S2-339, ambos flujos deben adquirir los bloqueos en este orden dentro
de la misma transacción local.

1. Bloquear la venta por empresa e ID.
2. Comprobar estado y elegibilidad de la venta.
3. Bloquear la cuenta asociada antes de actualizarla.
4. Consultar cobros efectivos y sus reversiones en Sales cuando exista esa persistencia.
5. Aplicar la transición bajo los bloqueos. Rechazar con 409 la cancelación
   con algún cobro efectivo y el abono contra una venta cancelada.

### Idempotencia de creación (S2-381)

Envía exactamente un header `Idempotency-Key` de 1 a 128 caracteres ASCII
`[A-Za-z0-9._~-]`. Conserva la clave y el cuerpo para reintentar la misma
venta. Una clave ausente, repetida o inválida devuelve `400`.

Sales comprueba `SALES_WRITE` con Company en cada petición. La clave distingue
empresa autorizada, UUID del actor autenticado y operación `POST /api/v1/sales`.
Un reintento idéntico devuelve el `201` y el JSON original. Una clave asociada
a otro payload devuelve `409 IDEMPOTENCY_CONFLICT`. Si otra instancia está
procesando la clave, Sales devuelve `409 IDEMPOTENCY_IN_PROGRESS` con
`Retry-After: 1`. Usa la misma clave y el mismo cuerpo al reintentar.

El fingerprint v1 aplica SHA-256 a campos UTF-8 separados por saltos de línea.
Incluye operación, versión, UUID del cliente, modalidad, fecha y pares de UUID
del artículo y cantidad en orden. Las cantidades equivalentes pierden ceros
decimales finales. La fecha ausente o nula se representa por `-`. La fecha
explícita se representa en ISO y se distingue de solicitar el valor por
defecto. El reloj se consulta durante la preparación de una venta nueva.
Cuando hay `dueDate`, el fingerprint v2 usa el mismo orden y agrega la fecha
de vencimiento después de la fecha de venta. Los replays v1 conservan el
cálculo original y se resuelven antes de validar un vencimiento nuevo.
El JSON original queda guardado como versión 1 junto con el código HTTP.
Los reintentos sirven ese JSON directamente, incluidos su fecha, snapshots y
orden de líneas. Si cambia el algoritmo, conserva el cálculo para versiones
aún retenidas antes de habilitar otra versión.

PostgreSQL reserva la clave con una restricción única. La reserva temporal
dura `SALES_IDEMPOTENCY_LEASE` (predeterminado `5m`). Sales consulta Catalog
fuera de la transacción SQL. Antes de asignar el código, una transacción
bloquea la fila y comprueba el token y la vigencia de la reserva. Venta,
líneas, cuenta de crédito, contador y resultado se confirman juntos. El token impide que un
proceso desplazado confirme una venta o libere la reserva de su sucesor.
Al fallar la operación, Sales intenta liberar su reserva. Si la instancia
cae, un reintento idéntico puede reclamar la reserva al vencer.

La configuración `SALES_IDEMPOTENCY_RETENTION` (predeterminado `7d`)
establece el vencimiento registrado al completar una clave. Sales conserva
las claves completadas tras ese vencimiento para reproducir el resultado y
proteger contra duplicados. Cada operación nueva requiere una clave distinta.
Las claves completadas eliminadas antes de V5 no se pueden reconstruir.

La limpieza operativa solo elimina reservas vencidas que nunca se completaron.
Ejecuta lotes pequeños en Sales DB con el rol de Sales. `SKIP LOCKED` evita
filas que se están finalizando. Este criterio preserva las claves confirmadas
y su respuesta. Automatizar la limpieza queda pendiente de S2-408.

```sql
WITH expired AS (
    SELECT company_id, actor_id, operation, idempotency_key
    FROM sales.sale_idempotency_keys
    WHERE sale_id IS NULL AND expires_at <= statement_timestamp()
    ORDER BY expires_at
    LIMIT 500
    FOR UPDATE SKIP LOCKED
)
DELETE FROM sales.sale_idempotency_keys
USING expired
WHERE sale_idempotency_keys.company_id = expired.company_id
  AND sale_idempotency_keys.actor_id = expired.actor_id
  AND sale_idempotency_keys.operation = expired.operation
  AND sale_idempotency_keys.idempotency_key = expired.idempotency_key;
```

Micrometer registra `sales.idempotency.resolve` y
`sales.idempotency.requests`, con resultados `created`, `replay`,
`conflict`, `in_progress` y `persistence_failure`. Las métricas omiten
claves, cuerpos y credenciales. Los perfiles Maven `smoke` y `regression`
incluyen el flujo HTTP con PostgreSQL y WireMock.

### Emitir una factura interna

La entrada pública para una operación comercial nueva es POST /api/v1/invoices.
El alta de venta independiente queda desactivada por defecto. Durante una
migración se puede habilitar mediante sanigest.sales.legacy-creation-enabled.

Para emitir una factura con productos, Sales verifica el seguimiento en Inventory
usando el tenant y la sesión de la solicitud. Configura SALES_INVENTORY_ORIGIN
con el origen de Inventory. Compose ya lo configura. Los productos con stock
controlado se rechazan hasta habilitar la reserva de existencias.

`POST /api/v1/sales/{saleId}/invoices` requiere JWT Bearer, una membresía
empresarial con `SALES_WRITE` y un único header `Idempotency-Key`. Se envía sin
cuerpo. El [contrato OpenAPI de emisión](openapi-invoices.yaml) describe el
`201`, los campos históricos y los errores de la operación.

El documento `INTERNAL / NOT_FISCAL` lleva un número comercial
`FAC-AAAA-NNN` por empresa y año de emisión en `America/Santo_Domingo`.
No genera NCF ni es un comprobante válido para la DGII. La emisión es manual
y no modifica las cuentas por cobrar ni registra cobros o movimientos de caja.

La migración V6 agrega las facturas, sus líneas y su contador. Sales copia la
fecha, el cliente, las líneas ordenadas y el total de la venta. Company agrega
`companyName` al resultado interno de membresía `SALES / WRITE`, mediante el
contrato autenticado de `common-company-access`. Despliega esa versión de
Company antes de habilitar nuevas emisiones. Si la membresía puede verificarse
pero su nombre empresarial falta o es inválido, una nueva emisión devuelve
`503`. El nombre queda almacenado junto con el identificador de la empresa.

Las claves se guardan con la factura confirmada y se limitan a empresa,
usuario y operación. Un reintento autorizado de la misma venta devuelve el
JSON original con `201`. Una clave usada con otra venta devuelve `409`.
La emisión y la cancelación bloquean la misma fila de venta. Cancelar una
venta con una factura vigente devuelve `409`.

### Registro de cobros de factura (S2-339)

`POST /api/v1/invoices/{id}/payments` requiere el permiso `SALES_WRITE` en la
empresa activa y un `Idempotency-Key`. Envía el nombre y saldo mostrados junto
con los medios e importes confirmados.

```json
{
  "clientName": "Cliente",
  "balance": "100.00",
  "lines": [
    {"method": "CASH", "amount": "30.00"},
    {"method": "TRANSFER", "amount": "20.00", "reference": "REF-2026"}
  ],
  "notes": "Abono parcial"
}
```

El 201 devuelve el identificador del pago, la factura, la cuenta por cobrar,
el operador, el turno, el importe aplicado, el nuevo saldo y las líneas.
La misma clave por empresa y operador devuelve el resultado original. Se
rechazan con 409 las claves reutilizadas con otro cuerpo o factura, las
facturas inactivas o sin cuenta por cobrar, el saldo o cliente desactualizado
y los importes superiores al saldo.

Sales bloquea la venta y la obligación antes de confirmar. Registra el cobro,
actualiza el saldo y encola un evento por línea en la misma transacción.
El puerto `PaymentAdmission` requiere que Finance valide el turno activo del
operador y resuelva la cuenta destino de cada línea. La ruta devuelve 503
hasta que exista un adaptador de admisión de Finance.
