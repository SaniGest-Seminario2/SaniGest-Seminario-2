# SaniGest

Backend de SaniGest basado en microservicios con Java, Spring Boot y PostgreSQL.

## Documentación arquitectónica

El [contrato funcional v0.8](docs/SaniGest_T1001_Contrato_Funcional_v0.8.md)
y el [ADR-002](docs/adr/ADR-002-evolucion-arquitectonica-multiempresa-inventario-finanzas.md)
definen el objetivo de evolución. Consulta la
[matriz de pantallas y APIs](docs/traceability/ui-backend-matrix-v0.8.md)
para conocer el estado de las rutas.

El [contrato v0.7](docs/SaniGest_T1001_Contrato_Funcional_v0.7.md)
registra el alcance histórico. El
[ADR-001](docs/adr/ADR-001-propiedad-datos-comunicacion-servicios.md)
mantiene vigentes sus decisiones de propiedad y comunicación según el ADR-002.

## Requisitos

- Java 21
- Maven 3.6.3 o posterior
- Docker 24 o posterior para dependencias locales y pruebas de integración

Comprueba las versiones instaladas:

```bash
java -version
mvn -version
docker --version
```

## Estructura del monorepo

```text
.
├── services/               Gateway, Identity, Company, Catalog, Inventory y Sales
├── libraries/              Librerías técnicas compartidas
├── infrastructure/         Imágenes de servicios, PostgreSQL local y verificaciones de Compose
├── config/                 Reglas de calidad del build
├── scripts/ci/             Comandos compartidos de automatización
├── docs/                   Contratos, ADR, guías de desarrollo y módulos previstos
└── .github/                Workflows y acciones reutilizables
```

El [catálogo de servicios](services/README.md) describe cada módulo y enlaza
la [guía para añadir un servicio](docs/development/new-service.md). El
[mapa de evolución](docs/roadmap/README.md) describe los módulos previstos.
Cada servicio mantiene su modelo de dominio, migraciones y configuración.
El [catálogo de librerías](libraries/README.md) documenta los componentes
compartidos.

## Compilar y probar

Desde la raíz:

```bash
mvn -B verify
```

Para reconstruir desde cero:

```bash
mvn -B clean verify
```

En cada PR, Code CI compila el reactor, ejecuta las pruebas unitarias y las
pruebas de integración del Gateway, y comprueba la cobertura. Infrastructure CI
valida Compose, ejecuta las pruebas de humo y construye las imágenes de
aplicación. En los PR hacia `main`, las verificaciones de Company, Identity,
Catalog y Sales se ejecutan según las rutas modificadas, incluidas sus
dependencias compartidas.

Cada push a `main`, incluidos los merges, activa Code CI, Infrastructure CI y
las verificaciones de los cuatro servicios sin filtros de rutas. Los reportes
de pruebas y cobertura se conservan durante siete días.

En la protección de `main` en GitHub, configura `Syntax and compilation`,
`Unit tests` y `Coverage gate` como checks obligatorios. Los workflows de
servicio quedan fuera de esa lista porque pueden omitirse en un PR según las
rutas modificadas.

## Formato y análisis estático Java

El POM padre aplica las mismas reglas a todos los servicios y librerías Maven.
Spotless es la autoridad del formato automático y Checkstyle valida convenciones
que no deben corregirse silenciosamente.

```bash
# Corregir automáticamente formato e imports
mvn spotless:apply

# Comprobar formato sin modificar archivos
mvn spotless:check

# Ejecutar análisis estático
mvn checkstyle:check

# Ejecutar todos los gates, incluidas las pruebas
mvn -B verify
```

Los checks se enlazan a la fase Maven `validate`, por lo que también se ejecutan
antes de `compile`, `test`, `package` y `verify`. Las reglas viven en
`config/checkstyle`. En el código de producción, el control de imports evita
que el dominio dependa de Spring, HTTP o persistencia, que la capa de
aplicación importe adaptadores concretos y que los controladores accedan a
repositorios. Catalog ejecuta también reglas ArchUnit en Surefire para
verificar estas dependencias en el bytecode y mantener independientes sus
casos de uso por funcionalidad. Maven Enforcer impide que servicios o
librerías declaren dependencias directas o transitivas hacia artefactos
ejecutables del reactor, conforme al ADR-001.

Una excepción requiere modificar `config/checkstyle/suppressions.xml` mediante
pull request. Debe ser específica para el archivo y la regla, incluir el ticket
de Jira y explicar por qué corregir el código no es la opción más segura. No se
aceptan supresiones generales ni anotaciones dispersas para silenciar el build.

Al actualizar Spotless, Google Java Format o Checkstyle, se debe ejecutar primero
`spotless:apply` en una rama dedicada, revisar por separado el cambio mecánico y
finalizar con `mvn -B verify` antes de integrar la nueva versión.

Si Maven no está instalado, se puede ejecutar la misma verificación con Docker:

```bash
docker run --rm \
  --user "$(id -u):$(id -g)" \
  -v "$PWD:/workspace" \
  -v "$HOME/.m2:/var/maven/.m2" \
  -w /workspace \
  -e MAVEN_CONFIG=/var/maven/.m2 \
  maven:3.9.11-eclipse-temurin-21 mvn -B verify
```

## Pruebas de QA automatizadas

La regresión Java comprueba migraciones, restricciones y contratos HTTP de cada
servicio. Surefire ejecuta unitarias `*Test`; Failsafe ejecuta integración,
regresión y smokes `*IT`.

El smoke test de infraestructura levanta dependencias desechables y valida el
comportamiento real de PostgreSQL y Valkey:

```bash
sh infrastructure/qa/smoke-infrastructure.sh
```

Los smokes de aplicación están implementados como escenarios JUnit 5 etiquetados
con `smoke`. Arrancan cada servidor en un puerto aleatorio y ejecutan HTTP real;
Identity usa PostgreSQL 17 mediante Testcontainers y comprueba éxito, validación,
duplicados por casing, concurrencia, respuesta segura y persistencia BCrypt:

```bash
mvn -B -Psmoke verify
```

Los reportes Failsafe XML se publican siempre como artefacto del job, con una
entrada por clase y método de prueba. La regresión sin smokes se ejecuta con
`mvn -B -DexcludedGroups=smoke verify`. Las pruebas E2E contra la URL de un
ambiente AWS se incorporarán junto con el pipeline de despliegue.

La prueba exige Docker Compose, usa puertos alternos por defecto y elimina sus
contenedores y volúmenes al terminar. Comprueba health checks, autenticación de
Valkey, persistencia tras reiniciar PostgreSQL y que reiniciar la cache no afecte
los datos de negocio.
GitHub Actions ejecuta esta prueba en cada PR y cada push a `main`.

## Validar el arranque de Company Service

Company requiere PostgreSQL, Identity y las claves públicas descritas en la
[configuración de seguridad](docs/seguridad-s2-317.md). El puerto por defecto es
`8080` y se puede sobrescribir mediante `SERVER_PORT`.

Company Service expone únicamente los endpoints operativos necesarios:

| Endpoint | Propósito |
| --- | --- |
| `GET /actuator/health` | Estado general sin detalles internos |
| `GET /actuator/health/liveness` | Confirma que el proceso puede continuar ejecutándose |
| `GET /actuator/health/readiness` | Confirma que la instancia está lista para recibir tráfico |
| `GET /actuator/info` | Identifica nombre, artefacto, versión y fecha del build |

La respuesta pública no incluye componentes de salud, variables de entorno,
credenciales, cadenas JDBC ni tokens. Endpoints sensibles como `env` y
`configprops` no están expuestos. La metadata de `/actuator/info` se genera al
construir el artefacto con Maven, de modo que la versión corresponde al build
en ejecución.

## Entorno local con Docker Compose

La [guía de Docker y Postman](docs/development/docker-postman.md) muestra cómo
arrancar el stack completo, incluido el Gateway, y probar sus endpoints.

El Compose base ofrece dos perfiles. `infra` levanta PostgreSQL, Valkey y Kafka.
`apps` agrega Identity, Company, Catalog, Inventory y Sales. Crea primero el archivo local de variables que Git ignora:

```bash
cp .env.example .env
```

Los valores de `.env.example` son credenciales ficticias exclusivas para local;
se deben reemplazar en `.env` cuando el entorno lo requiera. Nunca se deben
copiar secretos reales al archivo de ejemplo ni reutilizar estas contraseñas en
otro ambiente.

Para ejecutar sólo las dependencias y arrancar las aplicaciones desde Maven:

```bash
docker compose --profile infra up -d --wait
docker compose ps
```

Genera las claves RSA locales según la [configuración JWT](docs/seguridad-s2-317.md)
antes de construir y ejecutar las aplicaciones. Genera también las claves dedicadas de Catalog e Inventory según la
[guía del contexto empresarial](docs/development/company-context.md).
Inventory expone el puerto local 8086. Para actualizar un volumen existente,
ejecuta el [bootstrap dirigido de Inventory](services/inventory-service/README.md)
antes de iniciar las aplicaciones.

```bash
docker compose --profile apps up -d --build --wait
docker compose --profile apps ps
```

Las dependencias pertenecen a ambos perfiles para que el comando `apps` sea
portable entre versiones de Compose. `depends_on` espera los health checks
de cada dependencia antes de crear los servicios que la necesitan.

La red usa aislamiento interno por defecto. Si Docker omite la publicación en
redes internas, configura `SANIGEST_NETWORK_INTERNAL=false` en `.env` antes
de crear la red de desarrollo. Las publicaciones están ligadas a `127.0.0.1`.
PostgreSQL, Valkey,
Kafka quedan disponibles en `localhost:${POSTGRES_PORT:-5432}`,
`localhost:${SPRING_DATA_REDIS_PORT:-6379}` y `localhost:${KAFKA_PORT:-9092}`.
Company, Identity, Catalog y Sales usan `COMPANY_SERVICE_PORT` (8080),
`IDENTITY_SERVICE_PORT` (8081), `CATALOG_SERVICE_PORT` (8083) y
`SALES_SERVICE_PORT` (8084), respectivamente. Consulta la configuración y las
pruebas en los README de [Catalog](services/catalog-service/README.md) y
[Sales](services/sales-service/README.md). Dentro de la red interna de Compose
se usan `postgres:5432`, `valkey:6379` y `kafka:29092`; `localhost` nunca debe
usarse entre contenedores.

El bootstrap crea las bases y roles `sanigest_identity`, `sanigest_company`,
`sanigest_catalog`, `sanigest_sales`, `sanigest_expense` y `sanigest_finance`.
Cada rol puede conectarse y escribir únicamente en su base. La cuenta
`POSTGRES_ADMIN_USER` queda reservada para administración local y no debe
configurarse en ningún microservicio. Los nombres son deliberadamente estables;
las contraseñas se suministran mediante las variables `*_DB_PASSWORD` de `.env`.

Con PostgreSQL e Identity en ejecución, exporta las variables de `.env` y
arranca Company con el perfil `local`:

```bash
set -a
. ./.env
set +a
export JWT_PUBLIC_KEY_LOCATION="file:${JWT_PUBLIC_KEY_FILE}"
mvn -pl services/company-service -am -DskipTests package
SPRING_PROFILES_ACTIVE=local java -jar services/company-service/target/company-service-0.1.0-SNAPSHOT.jar
```

### Operación cotidiana

Consulta logs de todo el stack o de un componente:

```bash
docker compose --profile apps logs -f --tail=100
docker compose logs -f company-service
```

Detén el entorno conservando PostgreSQL y Kafka:

```bash
docker compose --profile apps down --remove-orphans
```

Los volúmenes `sanigest_postgres-data` y `sanigest_kafka-data` conservan sus
datos. Valkey no tiene volumen porque sólo almacena cache reconstruible. Para
reiniciar deliberadamente todo el estado local:

```bash
docker compose --profile apps down --volumes --remove-orphans
```

Si la conexión falla, verifica `docker compose ps`, el puerto y que las
variables cargadas en la terminal coincidan con `.env`. No se deben versionar
contraseñas reales ni reutilizar estas credenciales fuera del entorno local.

### Operación de la cache local

Valkey contiene únicamente datos derivados y reconstruibles; PostgreSQL sigue
siendo la fuente de verdad. Por eso el contenedor se inicia sin persistencia
RDB ni AOF y reiniciarlo o limpiar sus claves no elimina datos de negocio. Cada
integración debe aplicar un TTL explícito. El valor local recomendado es
`CACHE_DEFAULT_TTL=5m`, que podrá ajustarse por servicio o tipo de cache cuando
se implemente la integración de Spring Cache.

Para inspeccionar la cache sin escribir la contraseña en el historial del
shell, usa el valor disponible dentro del contenedor:

```bash
docker compose exec valkey sh -c \
  'valkey-cli --no-auth-warning -a "$VALKEY_PASSWORD" INFO keyspace'
docker compose exec valkey sh -c \
  'valkey-cli --no-auth-warning -a "$VALKEY_PASSWORD" SCAN 0'
```

Para limpiar exclusivamente la cache local:

```bash
docker compose exec valkey sh -c \
  'valkey-cli --no-auth-warning -a "$VALKEY_PASSWORD" FLUSHALL'
```

### Validaciones automatizadas de Compose

La validación rápida comprueba perfiles, health checks, dependencias, red,
puertos y política de persistencia con `jq` y sin arrancar contenedores.
El smoke test
levanta infraestructura desechable y además prueba aislamiento/persistencia de
PostgreSQL, autenticación/volatilidad de Valkey y operaciones reales de topics
en Kafka:

```bash
sh infrastructure/qa/validate-compose.sh
sh infrastructure/qa/smoke-infrastructure.sh
```

También se puede reiniciar Valkey con una cache vacía sin afectar PostgreSQL:

```bash
docker compose restart valkey
```

## Convenciones

- Nombres de módulos y artefactos en `kebab-case`.
- Paquetes Java bajo `com.sanigest`.
- Java compilado con `--release 21`.
- Dependencias Spring administradas por los BOM de Spring Boot y Spring Cloud.
- Versiones `SNAPSHOT` únicamente para módulos propios durante el desarrollo.
- Una librería común se extrae solo después de existir al menos dos consumidores
  y nunca contiene lógica de dominio de un servicio.
