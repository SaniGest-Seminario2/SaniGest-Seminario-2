# JWT y autorización de empresa

Gateway, Identity, Company, Catalog y Sales validan localmente los JWT de acceso de Identity.
Configurar `JWT_ISSUER`, `JWT_AUDIENCE` y `JWT_PUBLIC_KEY_LOCATION` en los
procesos que validan el JWT. La clave pública RSA usa PEM X.509 y al menos 2048 bits. Solo
Identity recibe `JWT_PRIVATE_KEY_LOCATION` (PEM PKCS#8). Usar ubicaciones
`file:` con archivos montados de solo lectura y permisos para el usuario del
proceso. Compose monta los archivos indicados en `JWT_PUBLIC_KEY_FILE` y
`JWT_PRIVATE_KEY_FILE`, con estos valores locales por defecto:

```sh
mkdir -p .secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out .secrets/jwt-private.pem
openssl pkey -in .secrets/jwt-private.pem -pubout -out .secrets/jwt-public.pem
```

El proceso de Identity en Compose usa UID 10001 y necesita permiso de lectura
sobre su clave privada. En Linux con ACL, concédelo con
`setfacl -m u:10001:r .secrets/jwt-private.pem`. La migración a RS256 invalida
los JWT HS256 previos.
Una rotación coordinada invalida los tokens firmados con la clave anterior.
El TTL se controla en Identity con `JWT_ACCESS_TOKEN_TTL` (15 minutos por defecto).

Company consulta la cuenta autenticada en Identity con el JWT original en cada
operación de empresa. Configurar `IDENTITY_SERVICE_URL` con el origen HTTP(S)
directo de Identity, mediante red privada y HTTPS en despliegues remotos.
`GET /internal/v1/account-authorization` está fuera del routing público y
valida el JWT independientemente. Devuelve 204 con `X-Account-Type` para cuentas
activas, 403 para cuentas ausentes o desactivadas y 401 si falla el JWT.
La llamada tiene conexión de 500 ms, timeout de 2 s y hasta 20 consultas
concurrentes por instancia. Un error de transporte o una respuesta inesperada
devuelve 503 sin ejecutar la operación.

Company consulta sus membresías en PostgreSQL, incluidas las respuestas de
reintentos idempotentes. La actualización y el reintento bloquean la fila de
membresía durante la transacción. La revocación confirmada se aplica a las
consultas posteriores. Una petición que ya verificó la cuenta puede terminar
si Identity confirma la desactivación después de esa verificación. No se guarda
caché de autorización. Logout revoca la sesión de refresh conforme al flujo
existente y un JWT de acceso puede conservar su validez criptográfica hasta
expirar. La cuenta desactivada pierde autorización dinámica de Company en la
siguiente verificación, aunque el JWT siga vigente.

Las rutas de empresa propia son para cuentas REGULAR. Company ya gestiona
concesiones ADVISOR por empresa, módulo y alcance. Catalog y Sales usan
`common-company-access` para consultar a Company. La resolución REGULAR
vigente se basa en una membresía singular; la delegación usa una empresa
explícita. Las rutas internas quedan fuera del Gateway público.

[S2-454](https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-454)
evoluciona la selección por petición y la autorización por par usuario-empresa
según el [contrato objetivo v0.8](SaniGest_T1001_Contrato_Funcional_v0.8.md).
