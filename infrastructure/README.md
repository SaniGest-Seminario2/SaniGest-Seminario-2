# Infraestructura

Este directorio contendrá la infraestructura reproducible de SaniGest:

- Docker y Docker Compose para desarrollo local;
- Terraform para AWS;
- manifiestos Kubernetes con `kind` como ampliación local.

El MVP se despliega en AWS con ECR, ECS/Fargate y RDS PostgreSQL. EKS y MSK no se
crean por defecto. Los módulos Terraform se implementan en los tickets
`S2-390` a `S2-394`.

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
