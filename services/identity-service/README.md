# Identity Service

Gestiona el registro, la identidad de cuenta y las sesiones en
`sanigest_identity`.

- `api/controller`, `api/dto` y `api/error` definen el contrato HTTP.
- `application/usecase` agrupa registro, cuenta y sesión. Los casos de uso
  usan DTO, excepciones y puertos de aplicación.
- `domain/model` define el usuario y la sesión de renovación. Los enums de dominio viven en `domain/enums`.
- `infrastructure/persistence` separa entidades, repositorios Spring Data y
  adaptadores JPA. Hibernate es el proveedor JPA. Flyway aplica las migraciones
  en `src/main/resources/db/migration`.
- `infrastructure/security` implementa la emisión y validación de JWT y el
  hash de contraseñas.

Los puertos de salida se agrupan por persistencia, seguridad o integraciones según el servicio.

Las pruebas unitarias reflejan los paquetes del código. Las pruebas HTTP
y con PostgreSQL viven en el paquete `integration`.

Desde la raíz:

```bash
mvn -B -pl services/identity-service -am verify
```

Consulta el [arranque local](../../README.md) y el
[contrato funcional](../../docs/SaniGest_T1001_Contrato_Funcional_v0.7.md).

## Refresh tokens (S2-386)

El login conserva `accessToken`, `tokenType` y `expiresIn`, y añade de forma
aditiva `refreshToken` y `refreshExpiresIn`. El cliente existente transporta el
refresh token en el cuerpo JSON de `POST /api/v1/auth/refresh` y
`POST /api/v1/auth/logout`; TLS/HTTPS es obligatorio fuera de desarrollo.

Los tokens son aleatorios, de un solo uso y PostgreSQL solo almacena su hash
SHA-256. Cada refresh bloquea la fila actual, la marca usada y crea el sucesor
en la misma transacción. Una reutilización revoca toda la familia. Los tokens
expirados se rechazan y se limpian periódicamente; el TTL y el intervalo de
limpieza son configurables con `JWT_REFRESH_TOKEN_TTL` y
`SANIGEST_REFRESH_CLEANUP_DELAY`.

Logout revoca la sesión/familia de refresh tokens. Un access JWT ya emitido
conserva su validez hasta expirar; esta historia no introduce revocación de JWT
ya emitidos.
