# GitHub Actions con AWS OIDC

Crea el stack de CloudFormation desde una sesión administrativa de la cuenta dev
y en la región de despliegue. Sustituye los valores de ejemplo por los ARN de los
recursos autorizados. Para incluir varios recursos, separa los ARN de cada
parámetro con comas.

```bash
aws cloudformation deploy \
  --region us-east-1 \
  --stack-name sanigest-dev-github-oidc \
  --template-file infrastructure/aws/github-oidc.yml \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides \
    EcrRepositoryArns=arn:aws:ecr:us-east-1:123456789012:repository/sanigest-dev/company-service \
    EcsServiceArns=arn:aws:ecs:us-east-1:123456789012:service/sanigest-dev/company-service \
    'EcsTaskDefinitionArns=arn:aws:ecs:us-east-1:123456789012:task-definition/sanigest-dev-company-service:*' \
    EcsTaskRoleArns=arn:aws:iam::123456789012:role/sanigest-dev-ecs-execution
```

Si la cuenta ya tiene el proveedor de GitHub, pasa su ARN como
`ExistingProviderArn` y verifica que acepte la audiencia `sts.amazonaws.com`.
Incluye los roles de aplicación de ECS que se usen además del rol de ejecución.
Si el repositorio emite subjects con IDs inmutables, pasa el subject exacto de
`main` como `GitHubSubject`. El rol verifica audiencia y subject por igualdad.

En **Settings → Secrets and variables → Actions → Variables** del repositorio,
crea `AWS_DEV_DEPLOY_ROLE_ARN` con el valor de la salida `DeployRoleArn` y
`AWS_DEV_REGION` con la región del stack.

## Verificación en GitHub

1. Cuando el workflow **Check AWS OIDC** esté en `main` y las variables estén
   configuradas, ejecútalo seleccionando `main`. El paso STS debe completarse.
2. Ejecuta el mismo workflow desde una rama de prueba creada a partir de ese
   `main`. AWS debe denegar `AssumeRoleWithWebIdentity` y el paso STS debe
   quedar omitido.

El workflow solicita una sesión de 15 minutos. Los pasos de verificación no
modifican recursos de AWS y mantienen la identidad y las credenciales fuera
del log.

[GitHub OIDC para AWS](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws) ·
[Permisos IAM de ECS](https://docs.aws.amazon.com/service-authorization/latest/reference/list_ecs.html)

## Demo en AWS bajo demanda

Usa una sola EC2 Linux con volumen raíz EBS gp3 y Docker Compose. La instancia
puede usar la IPv4 pública asignada automáticamente para acceder por SSH.
Configura el grupo de seguridad para permitir SSH solo desde tu IP. Esta
ruta prescinde de ALB, NAT Gateway, Elastic IP, ECR y bases administradas.
Consulta el plan y saldo de la cuenta antes de crearla y elige el tipo
elegible más pequeño que soporte los servicios de tu demo. Compilar los
servicios en el host también requiere memoria y espacio en disco.

En el primer arranque, instala Docker con Compose v2 y prepara el repositorio
siguiendo la [guía de Docker y Postman](../../docs/development/docker-postman.md).
Desde tu equipo, arranca la instancia indicando su región e ID:

```bash
bash infrastructure/aws/demo-ec2.sh start "$AWS_REGION" "$INSTANCE_ID"
ssh -L 8085:127.0.0.1:8085 "$SSH_USER@$INSTANCE_PUBLIC_IP"
```

Usa la IP que imprime el script. En la sesión SSH, inicia el stack con el
comando `sanigest` de la guía. Después del primer build, ejecuta
`sanigest up -d --wait --wait-timeout 180` para reutilizar las imágenes.
Desde tu equipo, comprueba el túnel con
`curl -fsS http://127.0.0.1:8085/actuator/health/readiness`.
Si vas a mostrar otra versión, usa un commit que haya pasado **CI** y vuelve
a construir las imágenes.

Al terminar, ejecuta `sanigest stop` en la instancia y luego, desde tu equipo:

```bash
bash infrastructure/aws/demo-ec2.sh stop "$AWS_REGION" "$INSTANCE_ID"
```

El script espera hasta que EC2 confirme el estado `stopped`. En ese estado
cesa el cobro por cómputo y los datos de la demo quedan en EBS, que puede seguir
generando cargos. La IP pública automática puede cambiar al volver a arrancar.
Al finalizar el trimestre, exporta los datos que quieras conservar y elimina
la instancia y los volúmenes o snapshots sobrantes.

[Free Tier de EC2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-free-tier-usage.html) ·
[Costos de una instancia detenida](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-lifecycle.html)

## Deploy dev con ECS (S2-364)

Deja `AWS_DEV_DEPLOY_ENABLED` sin definir mientras uses la demo en EC2.

El workflow **Deploy dev** usa el SHA exacto de una ejecución exitosa de **CI**
en el `main` del repositorio. Para repetir un despliegue, ejecuta **CI** desde
`main`. Se omiten resultados de commits superados y se serializan los
despliegues de dev.

Con los servicios ECS, el ALB y los repositorios ECR ya provisionados, configura
estas variables del repositorio además de las dos del rol OIDC:

- `AWS_DEV_ECS_CLUSTER` con el nombre o ARN del clúster.
- `AWS_DEV_SERVICES` con un arreglo JSON que enumere los servicios provisionados.
  Usa el nombre del módulo como nombre del servicio ECS. `container` es opcional
  y por defecto toma el mismo nombre. Cada URL HTTPS debe responder con
  `{"status":"UP"}` desde el runner y apuntar al servicio indicado.
- `AWS_DEV_DEPLOY_ENABLED` con `true` cuando el entorno esté listo.

```json
[
  {
    "service": "company-service",
    "repository": "sanigest-dev/company-service",
    "healthUrl": "https://dev.example.com/company/actuator/health"
  }
]
```

Usa repositorios ECR con etiquetas inmutables, servicios ECS con actualización
rolling y al menos una tarea activa. Conserva en ECR las imágenes de las
revisiones que puedan restaurarse. Para tolerar cancelaciones del runner, habilita
también el circuit breaker de ECS con rollback. La sesión OIDC dura 60 minutos
por servicio. Los secretos de aplicación se inyectan mediante referencias a
Secrets Manager o Parameter Store en la definición de tarea de ECS.

La etiqueta de la imagen es el SHA completo. Cada servicio compara sus fuentes
y dependencias compartidas con el SHA de su imagen activa. Una imagen inicial
sin SHA reconocible se reemplaza en el primer despliegue. Una falla durante la
actualización o el smoke test restaura la revisión previa del servicio y deja
el job en rojo. Los demás servicios se procesan de forma independiente.

### Prueba de rollback en dev

1. Despliega un cambio de imagen y guarda el ARN de la revisión activa.
2. Configura temporalmente para ese servicio una URL de smoke que devuelva
   503. Integra un cambio inocuo que afecte su imagen y espera a que CI termine.
3. Verifica que el job de despliegue falle y que ECS vuelva a la revisión
   anterior con las tareas estables. Restablece la URL y ejecuta CI desde
   `main` para validar el reintento con el mismo SHA.

Si el job se interrumpe o no puede confirmar la restauración, usa el ARN
anterior registrado en el log y comprueba que vuelva a quedar activo y sano.

```bash
aws ecs update-service --cluster "$CLUSTER" --service "$SERVICE" \
  --task-definition "$PREVIOUS_TASK_ARN" >/dev/null
aws ecs wait services-stable --cluster "$CLUSTER" --services "$SERVICE"
aws ecs describe-services --cluster "$CLUSTER" --services "$SERVICE" \
  --query 'services[0].taskDefinition' --output text
```
