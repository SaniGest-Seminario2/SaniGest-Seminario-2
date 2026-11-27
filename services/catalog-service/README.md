# SaniGest Catalog Service

Catalog es el propietario de los maestros de clientes, proveedores, productos
y servicios según [ADR-001](../../docs/adr/ADR-001-propiedad-datos-comunicacion-servicios.md).
S2-318 entrega el arranque y los endpoints operativos. El modelo y las operaciones
de negocio se incorporan a partir de S2-319.

## Arranque local

Desde la raíz del monorepo, prepara `.env` a partir de `.env.example` y genera
las claves RSA con la [guía JWT](../../docs/seguridad-s2-317.md).

```sh
docker compose --profile apps up -d --build --wait
docker compose --profile apps ps
docker compose exec -T catalog-service wget -qO- http://localhost:8080/actuator/health/readiness
docker compose exec -T catalog-service wget -qO- http://localhost:8080/v3/api-docs
```

Los puertos publicados por defecto son Company `8080`, Identity `8081` y
Catalog `8083`. Para ejecutar solo Catalog en Compose, indica
`catalog-service` al final del comando `up`. Para acceder a `http://localhost:8083` o ejecutar Catalog desde el host, usa
`SANIGEST_NETWORK_INTERNAL=false` en `.env` al crear la red del proyecto. Si ya
existe, ejecuta `docker compose --profile apps down` antes de volver a levantarla.
Los puertos publicados quedan ligados a `127.0.0.1`.

Para ejecutar Catalog desde el host con PostgreSQL en Compose:

```sh
docker compose --profile infra up -d --wait postgres
mvn -pl services/catalog-service -am -DskipTests package
set -a
. ./.env
set +a
SPRING_PROFILES_ACTIVE=local JWT_PUBLIC_KEY_LOCATION="file:${JWT_PUBLIC_KEY_FILE}" \
  java -jar services/catalog-service/target/catalog-service-0.1.0-SNAPSHOT.jar
```

| Variable | Configuración |
| --- | --- |
| `SERVER_PORT` | Puerto HTTP del proceso. Compose usa `8080` internamente. |
| `CATALOG_SERVICE_PORT` | Puerto publicado por Compose y valor por defecto del proceso local. Predeterminado `8083`. |
| `CATALOG_DB_HOST` | Host obligatorio en ejecución normal. En `local` usa `localhost` por defecto. |
| `CATALOG_DB_PORT` | Puerto PostgreSQL. En `local` toma `POSTGRES_PORT` si se define. El valor final predeterminado es `5432`. |
| `CATALOG_DB_PASSWORD` | Contraseña externa del rol `sanigest_catalog`. |
| `JWT_ISSUER`, `JWT_AUDIENCE` | Emisor y audiencia obligatorios. En `local` son `sanigest` y `sanigest-api` por defecto. |
| `JWT_PUBLIC_KEY_LOCATION` | Recurso Spring de la clave pública RSA, por ejemplo `file:./.secrets/jwt-public.pem`. |
| `JWT_PUBLIC_KEY_FILE` | Ruta de la clave pública en el host para el montaje de Compose. |

El bootstrap de PostgreSQL provisiona la base y el rol `sanigest_catalog`.
Flyway aplica `db/migration` y crea el esquema `catalog`. Hibernate arranca en
modo `validate`. El perfil de pruebas `test` usa un puerto HTTP efímero y
PostgreSQL provisionado por Testcontainers.

## Endpoints y seguridad

| Endpoint público | Propósito |
| --- | --- |
| `GET /actuator/health` | Estado general. |
| `GET /actuator/health/liveness` | Estado del proceso. |
| `GET /actuator/health/readiness` | Disponibilidad del proceso y de PostgreSQL. |
| `GET /v3/api-docs` | Contrato OpenAPI 3.0.3. |

Las sondas publican el estado y la raíz de health enumera los grupos públicos.
El Resource Server valida la firma RS256, emisor, audiencia, vigencia y el UUID
de identidad mediante `common-security`. Las operaciones de clientes requieren
autorización empresarial para el permiso correspondiente.

### Ficha comercial de clientes (S2-456)

Cada operación requiere JWT y `X-Company-Id`. Catalog consulta a Company para
resolver el permiso vigente y aplica la empresa autorizada a las lecturas,
conteos y escrituras. Si Company está indisponible, devuelve `503`. La
política de acceso delegado de ADVISOR conserva su alcance. Las rutas de
clientes y sus referencias requieren autorización por membresía.

| Membresía activa | `CATALOG_READ` | `CATALOG_WRITE` |
| --- | --- | --- |
| OWNER, ADMIN | Permitido | Permitido |
| OPERATOR | Permitido | Requiere concesión explícita |
| VIEWER | Permitido | Denegado |

`POST /api/v1/clients` devuelve `201` con la ficha. El servidor asigna UUID,
empresa y estado `ACTIVE`. El alta requiere `name` y `clientType`.
v0.8 usa `NATURAL` para nombre completo y `LEGAL` para razón social. Jira
propuso `PERSON` y `LEGAL_ENTITY`. Por defecto se asignan `DO`, `CASH` y la
categoría empresarial `REGULAR`.

Los registros previos pueden conservar el perfil en `null`. Para completarlo
mediante PATCH, envía juntos `clientType`, `countryCode`, `paymentTerms` y
`categoryId`. El alta valida los campos de entrada y asigna identidad, empresa y
estado en el servidor.

El `taxId` es texto. Catalog recorta espacios exteriores y guarda vacío como
`null`. Para DO, elimina espacios y guiones entre dígitos y valida el
formato de 11 dígitos para cédula (NATURAL) o 9 para RNC (LEGAL). Se
conservan ceros iniciales. Para otros países se conserva el identificador
textual. Los documentos históricos conservan su formato, incluso cuando
dos filas coinciden tras quitar separadores. La comprobación por clave
canónica impide asignar de nuevo una identificación numérica nacional que
colisiona con esas filas dentro de la empresa.

`creditLimit` es una cadena decimal en DOP, respaldada por `BigDecimal` y
`NUMERIC(19,2)`. Admite hasta 17 dígitos enteros y dos decimales.
`null` indica ausencia de un límite configurado y `"0.00"` indica un límite
explícito de cero. Sales es propietario del crédito disponible y los saldos.

#### Listado, detalle y edición

- `GET /api/v1/clients` busca fragmentos literales de nombre, documento,
  correo y teléfonos sin distinguir mayúsculas. Combina `status`,
  `clientType`, `categoryId` y `paymentTerms` antes de paginar. `CASH` y
  `CREDIT` incluyen `BOTH`. Filtrar por `BOTH` exige coincidencia exacta.
  `status=ACTIVE` sirve como selector de ventas.
- `page` comienza en cero. `size` tiene valor predeterminado 20 y un máximo
  configurable de 100 por defecto. `sort` admite `name,asc` y `name,desc`,
  con UUID ascendente para desempates. Las páginas fuera de rango conservan
  los totales del filtro y devuelven `content` vacío.
- `GET /api/v1/clients/{id}` incluye clientes inactivos. Sales conserva el
  contrato de ID, nombre y estado. Si envía `expectedCompanyId`, Catalog
  valida la coincidencia y confirma con `X-Catalog-Company-Id`.
- `PATCH /api/v1/clients/{id}` conserva campos omitidos y limpia campos
  opcionales con `null`. Valida el resultado de tipo/documento y de
  país/provincia/municipio. Nombre, tipo, país, modalidad y categoría
  de una ficha ampliada requieren valor.
- `PATCH /api/v1/clients/{id}/status` cambia el estado entre `ACTIVE` e
  `INACTIVE`. Repetir el estado conserva la revisión. Una baja conserva
  UUID y vínculos históricos. Sales sigue validando el estado al vender.

Referencias inválidas, inactivas al asignarlas o de otra empresa producen
`400`. Un cliente inexistente o ajeno produce `404`. Un documento fiscal
duplicado o una revisión obsoleta produce `409`.

#### Categorías y referencias

- `GET /api/v1/clients/categories` devuelve ID, código, nombre y estado.
  Las categorías `REGULAR`, `FREQUENT`, `WHOLESALE` y `CORPORATE` se
  inicializan localmente de forma idempotente por empresa.
  La lectura incluye las categorías existentes para preservar los vínculos históricos.
- `GET /api/v1/clients/reference-data/{countries,provinces,municipalities,receipt-types}`
  publica códigos y etiquetas. Provincias recibe `countryCode` y
  municipios recibe además `provinceCode`. Países usa ISO 3166-1. La
  cobertura administrativa de esta versión se limita a las 32 demarcaciones
  de primer nivel y 158 municipios del corte dominicano versionado en V9.
  [ONE, División Territorial 2021](https://www.one.gob.do/media/zsrmjfzp/divisi%C3%B3n-territorial-2021.pdf)
- `CONSUMPTION`, `FISCAL_CREDIT`, `SPECIAL_REGIMES` y `GOVERNMENT` son
  preferencias habituales. La elegibilidad fiscal y la emisión se
  gestionan con los documentos comerciales.
  [DGII, guía de comprobantes fiscales](https://dgii.gov.do/publicacionesOficiales/bibliotecaVirtual/contribuyentes/facturacion/Documents/Comprobantes%20Fiscales/1-Guia%205-Comprobantes%20Fiscales.pdf)

### Indicadores y ficha financiera de clientes (S2-457)

- `GET /api/v1/clients` y `GET /api/v1/clients/all` comparten filtros y orden.
  Cada fila incluye `balance`. Cero indica que Sales confirmó que el cliente no
  tiene deuda pendiente. `null` indica que el saldo no estuvo disponible para
  esa consulta.
- `GET /api/v1/clients/summary` devuelve `totalClients`, `activeClients`,
  `clientsWithPendingBalance`, `totalPending` y `asOf` para la empresa.
- `GET /api/v1/clients/{id}/financial-summary` devuelve la ficha comercial con
  `balance`, `availableCredit`, `totalInvoiced`, `paymentsReceived` y `asOf`.
  El crédito disponible es el límite menos la deuda a crédito, con mínimo
  cero. Un cliente sin límite devuelve `availableCredit: null`.
- `GET /api/v1/clients/{id}/statement` devuelve `invoices` y `payments`,
  ambos del más reciente al más antiguo. Acepta `page` y `size` (20 por
  defecto, máximo 100) e indica si hay más registros en cada lista. El
  historial incluye clientes inactivos y facturas anuladas con su estado.

Las consultas financieras requieren lectura de Catalog y Sales en la misma
empresa seleccionada con `X-Company-Id`. Catalog consulta Sales a través de
`CATALOG_SALES_ORIGIN`, configurado en Compose.

### Ficha comercial y artículos de proveedores (S2-458)

Se conservan las cinco rutas de proveedores. `POST` usa `201` y asigna
empresa, UUID y estado `ACTIVE`. Omitir `paymentTerms` aplica `CASH`.
`supplyType` y `categoryId` son opcionales. La cédula y el RNC admiten
11 y 9 dígitos respectivamente. Se quitan espacios y
guiones y se conservan los ceros iniciales. Los identificadores históricos
retienen su formato y se comprueban como posibles colisiones antes de asignar
un documento dominicano normalizado.

`PATCH /api/v1/suppliers/{id}` conserva campos omitidos y admite `null` en
campos opcionales. Nombre y contacto requieren valor. `creditDays` admite
`null` sin plazo, cero para vencimiento inmediato y cualquier entero positivo.
Un plazo configurado requiere `CREDIT` o `BOTH`.

`GET /api/v1/suppliers` combina `search`, `status`, `supplyType`,
`paymentTerms` y `categoryId` antes de paginar. Busca fragmentos literales de
nombre, identificación y contacto sin distinguir mayúsculas. Para tipo de
suministro y condición de pago, un valor individual incluye `BOTH`. El filtro
`BOTH` exige coincidencia exacta. `sort` admite `name,asc` y `name,desc`, con
UUID ascendente en empates. `page` empieza en 0 y `size` tiene valor
predeterminado 20. El máximo configurable
`sanigest.catalog.suppliers.max-page-size` tiene valor predeterminado 100.
`status=ACTIVE` sirve como selector.

- `GET /api/v1/suppliers/categories` inicializa las siete categorías por
  empresa de forma idempotente y devuelve ID, código, nombre y estado.
  Una nueva asignación requiere una categoría activa de la misma empresa.
- `GET /api/v1/suppliers/{id}/items` y `GET
  /api/v1/catalog-items/{id}/suppliers` consultan en ambas direcciones.
  Admiten `search`, `status` del vínculo, `page` y `size`. Sin filtro de
  estado incluyen los vínculos retirados.
- `PUT /api/v1/suppliers/{id}/items` devuelve `204` y reemplaza el conjunto
  activo completo de hasta 100 artículos. Enviar `{"items":[]}` retira todos
  los vínculos activos. Cada vínculo requiere un `catalogItemId` distinto.
  `offeredCost` es una cadena decimal DOP con hasta 15 dígitos enteros y 4
  decimales. `null` representa costo desconocido y `"0"` costo explícito cero.
  `supplierReference` es opcional y `primary` es `false` por defecto. Reenviar
  el mismo conjunto conserva las fechas de los vínculos que siguen vigentes.
  El cliente debe enviar el conjunto completo al editar.

Seleccionar `primary=true` con otro principal asignado produce `409`. Enviar
`replacePrimary=true` en el PUT autoriza transferir esa designación en la
misma transacción. La operación bloquea primero al proveedor y luego los
artículos implicados por UUID. Los reemplazos completos se aplican en serie.
PostgreSQL garantiza un único vínculo para cada par y como máximo un principal
por artículo y empresa.

La vigencia empieza al activar el vínculo. Omitir el vínculo en un PUT o
desactivar alguno de sus extremos cierra la vigencia y retira el principal. La
fila y su oferta quedan disponibles para consulta histórica. Un PUT posterior
puede reabrir la misma fila con una nueva fecha de inicio cuando ambos
extremos son activos. La reactivación del proveedor o artículo requiere un PUT
explícito para volver a habilitar los vínculos. Un proveedor con tipo sin
configurar necesita un `supplyType` compatible para asociarse. Cambiar el tipo
con vínculos activos incompatibles devuelve `409`.

Todas las rutas requieren JWT, `X-Company-Id` y el permiso vigente
`CATALOG_READ` o `CATALOG_WRITE`. Una categoría ajena se trata como referencia
inválida (`400`). Proveedores y artículos ajenos producen `404`. La escritura
conserva la revisión optimista de proveedor y artículo.

Expense utilizará la ficha comercial al validar compras y será propietario de
obligaciones, saldos, pagos y costos históricos de adquisición. Catalog conserva
el costo ofertado separado del costo estimado y del precio de venta.

### Productos y servicios (S2-459)

Todas las rutas del [contrato OpenAPI](src/main/resources/catalog-openapi.json)
requieren JWT, empresa seleccionada en `X-Company-Id` y autorización dinámica.
Estas operaciones usan la membresía empresarial de Catalog. El acceso delegado de ADVISOR sigue la política de cada ruta.

| Ruta bajo `/api/v1/catalog-items` | Capacidad |
| --- | --- |
| `GET /`, `POST /` | Listar y crear PRODUCT o SERVICE. |
| `GET /{id}`, `PATCH /{id}`, `PATCH /{id}/status` | Consultar y editar la ficha, activar o desactivar. |
| `GET /categories` | Consultar categorías propias de la empresa. |
| `GET /units`, `GET /tax-classifications` | Consultar referencias globales con CATALOG_READ. |
| `GET /summary` | Contar productos, servicios, activos e inactivos de toda la empresa. |
| `GET /{id}/history` | Consultar cambios en orden descendente, paginados. |

- SKU y código de barras admiten 64 caracteres. Ambos recortan espacios exteriores.
  El SKU se convierte a mayúsculas y el código de barras conserva texto y ceros
  iniciales. La unicidad empresarial incluye artículos inactivos.
- Las altas requieren unidad activa y clasificación vigente, tanto con precio
  numérico como en cadena. PATCH conserva lo omitido y permite limpiar campos
  opcionales con null. El tipo permanece estable. Cuando existe un proveedor
  principal, el detalle y el listado incluyen `primarySupplierId` y
  `primarySupplierName`. La creación también devuelve `unitDivisible`.
- Las cinco categorías iniciales se crean idempotentemente por empresa.
  Las categorías inactivas conservan sus artículos y se excluyen de nuevas
  asignaciones. El filtro `categoryId` puede combinarse con tipo, estado,
  disponibilidad y búsqueda literal por nombre, SKU, código de barras o categoría.
- UNIT, BOX y PACK son indivisibles. LB, KG, L y M admiten fracciones.
  Catalog publica esa propiedad para la validación futura de cantidades.
- ITBIS_18, ITBIS_16 y EXEMPT tienen versiones con inicio inclusivo y fin
  exclusivo. Las tasas se resuelven por fecha comercial en America/Santo_Domingo.
  Las versiones iniciales de referencia se definieron desde el 1 de enero de 2026.
  El mantenimiento de tasas se realiza mediante migraciones administradas.
  Un artículo sin tasa vigente devuelve ITBIS y precio final nulos.
- Se redondean a dos decimales con HALF_UP el ITBIS unitario y la suma de precio
  neto e ITBIS unitario. El beneficio es precio neto menos costo estimado,
  con cuatro decimales. El margen es beneficio dividido entre precio neto por
  cien, con dos decimales. Costo desconocido implica beneficio y margen nulos,
  y precio cero implica margen nulo. Sales calculará importes por línea en S2-461.
- Las altas habilitan ventas por defecto y requieren activar compras
  explícitamente. Un artículo activo es elegible para cada operación cuando
  su flag correspondiente es true. Compras desconocidas se representan como
  null en artículos históricos. Los vínculos de S2-458
  conservan su compatibilidad con esa forma histórica. Deshabilitar compras
  retira el proveedor principal en la misma transacción y mantiene consultable
  el vínculo. Volver a habilitarlas requiere una nueva designación explícita.
- El historial registra creaciones, cambios efectivos, estado y designaciones
  de proveedor con el actor autenticado. Una sustitución de proveedor registra
  el retiro y la nueva designación. Las operaciones idempotentes conservan la
  revisión y el historial. Las escrituras de mantenimiento sin JWT pueden tener
  actor nulo. Los artículos previos a V16 empiezan a generar historial al editarse.

**Transición de precios.** PostgreSQL conserva precio y costo unitario en
`NUMERIC(19,4)`. Los campos decimales nuevos se devuelven como cadenas y
`price` conserva el número JSON. Con
`sanigest.catalog.extended-prices-enabled=false` (valor seguro predeterminado),
las escrituras de precio respetan el rango histórico de `NUMERIC(14,2)` y
hasta dos decimales significativos. Los costos admiten cuatro decimales.
Activar la opción requiere desplegar primero S2-461, verificar el contrato
de cuatro decimales con Sales y coordinar la promoción entre servicios.
Sales actual rechaza precios fuera de su rango o escala y artículos
explícitamente no vendibles. Los snapshots confirmados conservan el precio
y los demás datos de la venta registrada.

Inventory (S2-460) proveerá las existencias físicas, almacenes y kardex.

## Verificación

```sh
mvn -pl services/catalog-service -am -DskipITs test
mvn -pl services/catalog-service -am -Psmoke verify
mvn -pl services/catalog-service -am -Pregression verify
mvn -pl services/catalog-service -am verify
```

Las pruebas de integración requieren un daemon Docker accesible. Aplican el
bootstrap de PostgreSQL del monorepo y prueban el contrato HTTP, JWT, JPA,
Flyway y los permisos entre las bases de Catalog, Company e Identity.

Clientes, proveedores y artículos requieren `X-Company-Id` en cada petición,
incluidas las consultas directas de Sales. El servicio consulta Company v2 para
la empresa seleccionada y limita las consultas de persistencia al tenant
autorizado. Consulta la [matriz de rutas](../../docs/development/company-context.md).

### Filtrar productos y servicios por existencias

`GET /api/v1/catalog-items?warehouseId={uuid}&availability=OUT&type=PRODUCT`
combina el filtro físico con los filtros comerciales y calcula el total antes
de devolver la página. Envía ambos parámetros de stock juntos. La respuesta
conserva el formato del listado general. El token debe permitir lectura en
Catalog e Inventory para la empresa seleccionada. El cliente de Inventory usa
`CATALOG_INVENTORY_ORIGIN`, configurado en Compose y con valor local
`http://localhost:8086` por defecto. Si Inventory falla o se agota el presupuesto
de lectura, la API devuelve 503 sin conteos parciales.
