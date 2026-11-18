# SaniGest

Backend de SaniGest basado en microservicios con Java, Spring Boot y PostgreSQL.

## Documentación arquitectónica

Los siguientes documentos se están incorporando mediante los PRs de `S2-293` y
`S2-303`; sus rutas se consideran parte estable de la estructura del monorepo:

- [Contrato funcional y mapa de APIs v0.7](docs/SaniGest_T1001_Contrato_Funcional_v0.7.md)
- [ADR-001: propietarios de datos y comunicación entre servicios](docs/adr/ADR-001-propiedad-datos-comunicacion-servicios.md)

El ADR-001 es la referencia obligatoria para definir nuevas historias, tablas,
integraciones síncronas y eventos entre servicios. Ningún módulo puede consultar
las tablas de otro servicio ni compartir sus entidades JPA.

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
├── services/               Microservicios desplegables e independientes
│   ├── api-gateway/        Entrada pública (reservado para S2-316)
│   ├── identity-service/   Identidad y sesiones (reservado para S2-304)
│   ├── company-service/    Módulo inicial compilable de Company
│   ├── catalog-service/    Maestros comerciales (reservado para S2-318)
│   ├── sales-service/      Ciclo de ventas (reservado para S2-331)
│   ├── expense-service/    Ciclo de gastos (reservado para S2-342)
│   └── finance-service/    Caja y reportes (reservado para S2-349)
├── templates/              Plantillas técnicas ejecutables para nuevos módulos
├── libraries/              Contratos técnicos compartidos ya demostrados
├── infrastructure/         Terraform, Docker y despliegue
├── docs/                   ADRs y documentación técnica
└── .github/workflows/      Automatización de CI/CD
```

Cada microservicio mantiene su modelo de dominio, persistencia, migraciones y
configuración. `libraries` no admite entidades JPA, repositorios ni reglas de
negocio compartidas.

## Compilar y probar

Desde la raíz:

```bash
mvn -B verify
```

Para reconstruir desde cero:

```bash
mvn -B clean verify
```

GitHub Actions ejecuta dos jobs por cada `push` y `pull_request`:

```bash
# Syntax, format and static analysis
mvn -B -DskipTests compile

# Unit tests
mvn -B verify
```

El job de pruebas se ejecuta únicamente después de una compilación correcta. Si
falla, los reportes Surefire y Failsafe disponibles se publican como artefactos
durante siete días.

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
`config/checkstyle`; no deben copiarse dentro de los servicios.

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
sh scripts/qa/smoke-infrastructure.sh
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
GitHub Actions ejecuta esta prueba en cada `push` y `pull_request`.

## Validar el arranque de Company Service

El módulo inicial carga el contexto Spring Boot sin depender de otros servicios
ni de una base local cuando se usa el perfil predeterminado:

```bash
mvn -pl services/company-service -am spring-boot:run
```

El servicio escucha en el puerto `8080` por defecto. Se puede cambiar sin
modificar el artefacto mediante `SERVER_PORT`:

```bash
SERVER_PORT=8081 mvn -pl services/company-service -am spring-boot:run
```

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

El Compose de desarrollo ofrece dos perfiles: `infra` levanta sólo PostgreSQL,
Valkey y Kafka; `apps` levanta el stack completo, incluidas esas dependencias y
los microservicios ejecutables. Crea primero el archivo local de variables, que
Git ignora:

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

Para construir y ejecutar Company Service junto con sus dependencias saludables:

```bash
docker compose --profile apps up -d --build --wait
docker compose --profile apps ps
```

Las dependencias pertenecen a ambos perfiles para que el comando `apps` sea
portable entre versiones de Compose. `depends_on` espera sus health checks antes
de crear Company Service. El perfil sólo incorpora aplicaciones que ya tienen
un artefacto ejecutable; los directorios reservados se añadirán conforme se
implementen.

Todos los puertos publicados están ligados a `127.0.0.1`. PostgreSQL, Valkey,
Kafka y Company Service quedan disponibles en `localhost:${POSTGRES_PORT:-5432}`,
`localhost:${SPRING_DATA_REDIS_PORT:-6379}`, `localhost:${KAFKA_PORT:-9092}` y
`localhost:${COMPANY_SERVICE_PORT:-8080}`. Dentro de la red interna de Compose
se usan `postgres:5432`, `valkey:6379` y `kafka:29092`; `localhost` nunca debe
usarse entre contenedores.

El bootstrap crea las bases y roles `sanigest_identity`, `sanigest_company`,
`sanigest_catalog`, `sanigest_sales`, `sanigest_expense` y `sanigest_finance`.
Cada rol puede conectarse y escribir únicamente en su base. La cuenta
`POSTGRES_ADMIN_USER` queda reservada para administración local y no debe
configurarse en ningún microservicio. Los nombres son deliberadamente estables;
las contraseñas se suministran mediante las variables `*_DB_PASSWORD` de `.env`.

Exporta las mismas variables y arranca Company con el perfil `local`:

```bash
set -a
. ./.env
set +a
mvn -pl services/company-service -am spring-boot:run \
  -Dspring-boot.run.profiles=local
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
puertos y política de persistencia sin arrancar contenedores. El smoke test
levanta infraestructura desechable y además prueba aislamiento/persistencia de
PostgreSQL, autenticación/volatilidad de Valkey y operaciones reales de topics
en Kafka:

```bash
sh scripts/qa/validate-compose.sh
sh scripts/qa/smoke-infrastructure.sh
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
