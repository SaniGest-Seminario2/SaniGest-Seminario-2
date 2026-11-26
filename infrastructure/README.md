# Infraestructura

El [Compose raíz](../docker-compose.yml) define los perfiles locales
`infra` (PostgreSQL, Valkey y Kafka) y `apps` (infraestructura, Company e
Identity). Conserva el punto de entrada `docker compose` desde la raíz y la
configuración de ejemplo en [`.env.example`](../.env.example).

| Ruta | Responsabilidad |
| --- | --- |
| [`docker/service.Dockerfile`](docker/service.Dockerfile) | Imagen Maven/Java para Company e Identity, seleccionada con el argumento `SERVICE` |
| [`postgres/init`](postgres/init) | Creación de roles y bases de datos locales |
| [`qa`](qa) | Validación estática y smoke test de Compose |

Ejecuta desde la raíz:

```bash
sh infrastructure/qa/validate-compose.sh
sh infrastructure/qa/smoke-infrastructure.sh
```

La validación requiere `jq`. El smoke test levanta un stack Docker temporal
y elimina sus volúmenes al finalizar.

El despliegue del MVP en AWS con ECR, ECS/Fargate y RDS PostgreSQL está previsto
en los tickets `S2-390` a `S2-394`.

La [configuración OIDC de GitHub Actions](aws/README.md) permite obtener
credenciales temporales para el despliegue a dev.

## PostgreSQL local

`postgres/init/10-create-service-databases.sh` inicializa una base y un rol de
login por microservicio. El script usa identificadores fijos, recibe las
contraseñas desde el entorno, revoca el acceso público a cada base y puede
ejecutarse repetidamente sin duplicar objetos. No crea tablas ni relaciones
entre servicios; las migraciones de cada servicio son las únicas responsables
de su esquema.

El entrypoint oficial lo ejecuta automáticamente al crear un volumen vacío. Para
reaplicar privilegios o rotar las contraseñas de un volumen existente:

```bash
docker compose exec postgres \
  /docker-entrypoint-initdb.d/10-create-service-databases.sh
```

La cuenta administrativa sólo pertenece al runtime de infraestructura. Las
aplicaciones deben usar su correspondiente `*_DB_USER`, `*_DB_NAME` y
`*_DB_PASSWORD` definidos en `.env.example`.
