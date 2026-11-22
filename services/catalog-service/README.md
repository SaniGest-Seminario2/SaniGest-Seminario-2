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

### Alta de clientes

`POST /api/v1/clients` requiere un JWT de acceso en `Authorization: Bearer <token>`.
El cuerpo acepta `name` obligatorio y `taxId`, `email`, `phone` y `address`
opcionales. La respuesta exitosa es `201` con `id`, `name`, `taxId` y
`status: ACTIVE`. Los identificadores y el estado se asignan en el servidor.
El identificador fiscal conserva mayúsculas, minúsculas y separadores. Catalog
elimina el espacio en blanco ASCII de los extremos y guarda un valor vacío como
`null`. PostgreSQL garantiza unicidad por empresa para los valores presentes.

El cuerpo y los encabezados del solicitante no seleccionan la empresa.
Catalog obtiene el actor de `sub` y requiere una decisión vigente de Company
para ese actor y la operación de alta. Catalog devuelve `400` por datos
inválidos, `401` por JWT ausente o inválido, `403` ante una denegación de
Company y `409` por duplicados en la empresa autorizada.

Spring Method Security exige un JWT autenticado al invocar el caso de uso
`CreateClient`. El caso de uso aplica `CATALOG_WRITE` y usa la empresa que
devuelve esa misma decisión para persistir el cliente. Cada ejecución consulta
Company una vez, sin cachear autorizaciones. Una instalación sin el adaptador
habilitado o con Company indisponible responde `503` y evita la escritura.

| Membresía activa | `CATALOG_READ` | `CATALOG_WRITE` |
| --- | --- | --- |
| OWNER, ADMIN | Permitido | Permitido |
| OPERATOR | Permitido | Requiere concesión explícita |
| VIEWER | Permitido | Denegado |

La política `CatalogAuthorization` distingue el acceso por membresía del
acceso delegado. Las operaciones que admitan ADVISOR deben proporcionar la
empresa solicitada y exigir en Company una concesión vigente para `CATALOG`
y el alcance exacto. Una concesión READ autoriza únicamente lectura. El alta
de S2-319 se limita a membresías activas y conserva su contrato HTTP.

Para futuras operaciones sobre registros existentes, cada consulta debe usar
la empresa resuelta por la autorización como parte del filtro de persistencia,
incluida la búsqueda por identificador. Un registro fuera de esa empresa se
trata como invisible conforme al contrato de la operación.

La configuración y el contrato interno están en
[common-company-access](../../libraries/common-company-access/README.md).

### Consulta de clientes

`GET /api/v1/clients?search=clinica&status=ACTIVE&page=0&size=20` requiere
`CATALOG_READ` en la membresía activa. La consulta usa la empresa devuelta por
Company. Este endpoint no admite un contexto de empresa para ADVISOR.

`search` busca un fragmento literal del nombre o del documento fiscal sin
distinguir mayúsculas. `status` admite `ACTIVE` e `INACTIVE` sin distinguir
mayúsculas. Ambos filtros se combinan y se omiten individualmente si quedan
vacíos al recortar espacios exteriores.

La página empieza en 0 y el tamaño predeterminado es 20. Se aceptan tamaños
desde 1 hasta 100 por defecto. El máximo se configura mediante
`sanigest.catalog.clients.max-page-size` y debe permitir el tamaño predeterminado.
La respuesta incluye `content`, `page`, `size`, `totalElements` y `totalPages`.
El orden es por nombre y UUID ascendentes. Una página fuera de rango conserva
los totales y devuelve `content` vacío. Los parámetros inválidos producen
`400` y la indisponibilidad de Company produce `503`.

### Detalle, edición y estado de clientes

- `GET /api/v1/clients/{id}` devuelve el detalle con `CATALOG_READ`.
- `PATCH /api/v1/clients/{id}` admite `name`, `taxId`, `email`, `phone` y `address`
  con `CATALOG_WRITE`. Omite los campos que quieras conservar. Envía `null` para
  limpiar un campo opcional. El nombre debe contener texto.
- `PATCH /api/v1/clients/{id}/status` acepta `{"status":"INACTIVE"}` o
  `{"status":"ACTIVE"}` con `CATALOG_WRITE`. Repetir el estado vigente devuelve
  el cliente sin incrementar su revisión.

Las tres rutas resuelven la membresía con Company y devuelven `404` para UUID
inexistentes o ajenos a esa empresa. Los PATCH devuelven `200` con el detalle
actualizado o `409` ante una escritura concurrente. El documento fiscal de un
cliente inactivo sigue reservado dentro de su empresa.

Los clientes inactivos conservan su UUID y aparecen en el detalle y en el
listado según el filtro de estado. Sales debe impedir seleccionar clientes
`INACTIVE` para nuevas ventas y conservar las referencias históricas.

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
