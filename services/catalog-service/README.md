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
de identidad mediante `common-security`. Cualquier otra ruta requiere autenticación y se deniega
hasta incorporar una política empresarial explícita. Los casos de uso futuros
deberán obtener el UUID de identidad del JWT verificado y resolver membresía
y permisos con Company antes de acceder a datos. El `companyId` de la
solicitud identifica la empresa solicitada.

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
