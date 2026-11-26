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
