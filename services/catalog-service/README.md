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
empresa y estado `ACTIVE`. El alta ampliada requiere `name` y `clientType`.
v0.8 usa `NATURAL` para nombre completo y `LEGAL` para razón social. Jira
propuso `PERSON` y `LEGAL_ENTITY`. Por defecto se asignan `DO`, `CASH` y la
categoría empresarial `REGULAR`.

El alta anterior sigue disponible en la misma ruta con `name`, `taxId`,
`email`, `phone` y `address`. En esa variante se ignoran los metadatos
legados `id`, `companyId`, `status` y `userId`. Cualquier campo ampliado
activa la validación del alta ampliada. Altas antiguas y filas anteriores a V10
conservan el perfil en `null`. Para completarlo mediante PATCH, envía juntos
`clientType`, `countryCode`, `paymentTerms` y `categoryId`.

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
- `POST /api/v1/clients/categories` crea una categoría activa por código y
  nombre. `PUT /api/v1/clients/categories/{id}` actualiza nombre y estado.
  Los códigos son estables y únicos por empresa. `REGULAR` admite cambios
  de nombre y permanece activa para el alta predeterminada. Desactivar
  otra categoría conserva los vínculos históricos.
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

### Ficha comercial y artículos de proveedores (S2-458)

Se conservan las cinco rutas de proveedores. `POST` usa `201` y asigna
empresa, UUID y estado `ACTIVE`. El alta histórica conserva el perfil
comercial sin configurar. Incluir un campo ampliado aplica `paymentTerms=CASH`
por defecto. `supplyType` y `categoryId` son opcionales. La cédula y el RNC
del alta ampliada admiten 11 y 9 dígitos respectivamente. Se quitan espacios y
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
- `POST /api/v1/suppliers/categories` crea una categoría activa con código
  estable y único. `PUT /api/v1/suppliers/categories/{id}` actualiza nombre y
  estado. Una baja conserva las referencias existentes. Una nueva asignación
  requiere una categoría activa de la misma empresa.
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

S2-459 integrará la elegibilidad de compras y podrá derivar el proveedor
principal del vínculo. Expense utilizará el selector y la ficha comercial al
validar compras, y será propietario de obligaciones, saldos, pagos y costos
históricos de adquisición. Catalog almacena los costos ofertados por separado
del precio de venta.

### Consulta y gestión de productos y servicios

El [contrato OpenAPI](src/main/resources/catalog-openapi.json) describe los filtros,
los cuerpos de edición y los errores. Company verifica la membresía en cada solicitud.
Las rutas de S2-325 usan la empresa de esa membresía y no reciben un contexto delegado
para ADVISOR. Un artículo inactivo sigue disponible en el detalle y puede reactivarse.

Catalog conserva el precio vigente en DOP. Al registrar una venta, Sales debe guardar
en cada línea un snapshot del nombre, la descripción y el precio unitario efectivo
con su moneda. Las lecturas y cálculos históricos de Sales deben usar ese snapshot,
de modo que las ediciones posteriores del catálogo no cambien ventas registradas.

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
