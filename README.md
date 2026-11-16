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

La regresión del servicio se ejecuta con `mvn -B verify` y comprueba las
migraciones de Company, sus restricciones y los contratos públicos de Actuator.
El smoke test de infraestructura levanta dependencias desechables y valida el
comportamiento real de PostgreSQL y Valkey:

```bash
sh scripts/qa/smoke-infrastructure.sh
```

La prueba exige Docker Compose, usa puertos alternos por defecto y elimina sus
contenedores y volúmenes al terminar. Comprueba health checks, autenticación de
Valkey, persistencia tras reiniciar PostgreSQL y que reiniciar la cache no afecte
los datos de negocio.
GitHub Actions ejecuta esta prueba en cada `push` y `pull_request`.

## Validar el arranque de Company Service

El módulo inicial carga el contexto Spring Boot sin depender de otros servicios:

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

## Dependencias locales: PostgreSQL y Valkey

Crea el archivo local de variables (está ignorado por Git) y levanta la base y
la cache:

```bash
cp .env.example .env
docker compose up -d company-postgres valkey
docker compose ps
```

Ambos contenedores deben aparecer como `healthy`. Valkey queda disponible sólo
en la interfaz local, en `localhost:${SPRING_DATA_REDIS_PORT:-6379}`. Los
servicios que se ejecuten dentro de la red `sanigest-local` deben usar
`valkey:6379` en lugar de `localhost`. La conexión se configura externamente con
`SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT` y
`SPRING_DATA_REDIS_PASSWORD`; no se deben incluir credenciales en el artefacto.

Exporta las mismas variables y arranca Company con el perfil `local`:

```bash
set -a
. ./.env
set +a
mvn -pl services/company-service -am spring-boot:run \
  -Dspring-boot.run.profiles=local
```

Para detener PostgreSQL sin perder los datos:

```bash
docker compose down
```

El volumen `sanigest_company-postgres-data` conserva los datos. Elimínalo sólo
cuando se quiera reiniciar deliberadamente la base local:

```bash
docker compose down --volumes
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
