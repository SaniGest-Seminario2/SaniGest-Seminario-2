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
[contrato objetivo v0.8](../../docs/SaniGest_T1001_Contrato_Funcional_v0.8.md).

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

## Consulta interna de elegibilidad de asesores (S2-451)

Company llama directamente a Identity por HTTPS en red privada con
`GET /internal/v1/accounts/{userId}/advisor-eligibility` y
`Authorization: Bearer <JWT técnico>`. La ruta queda fuera del Gateway público.
`userId` es un UUID canónico. La respuesta `200` contiene solo
`{"eligible":true}` para una cuenta ADVISOR activa. Una cuenta inexistente,
REGULAR o desactivada devuelve `200` con `{"eligible":false}`. Todas las
respuestas de la ruta incluyen `Cache-Control: no-store`.

- `400` indica UUID inválido tras autorizar la consulta.
- `401` indica credencial ausente o inválida. `403` indica sesión de usuario,
  identidad técnica distinta de Company o scope insuficiente.
- `503` indica indisponibilidad de la consulta. Company debe fallar cerrado
  ante cualquier error, timeout o respuesta malformada. Usa 500 ms de timeout
  de conexión y 2 s para la solicitud completa. Registra latencia, estado HTTP
  y fallos de dependencia sin registrar JWT ni UUID de destinatarios.

### Emisión y operación de credenciales

Company actúa como emisor del JWT técnico con una clave RSA dedicada de al
menos 2048 bits por ambiente. Guarda la clave privada de firma en el gestor
de secretos o usa una clave de firma administrada accesible solo por la
identidad de ejecución de Company. Identity recibe exclusivamente la clave
pública en formato PEM X.509 mediante
`IDENTITY_ADVISOR_ELIGIBILITY_PUBLIC_KEY_LOCATION` (por ejemplo,
`file:/run/secrets/company-advisor-public.pem`) y el emisor esperado mediante
`IDENTITY_ADVISOR_ELIGIBILITY_ISSUER`, distinto del emisor de sesiones y
específico del ambiente. Ambas variables vacías deshabilitan la autenticación
técnica. Una configuración incompleta o una clave de sesión reutilizada
impide el arranque.

Company firma un JWT de corta duración con una biblioteca JOSE. Por ejemplo,
con Nimbus JOSE y un firmante inicializado con la clave de Company:

```java
var now = java.time.Instant.now();
var claims = new com.nimbusds.jwt.JWTClaimsSet.Builder()
    .issuer(issuer) // Mismo valor aprovisionado en Identity.
    .subject("company-service")
    .audience("sanigest-identity-advisor-eligibility")
    .claim("scope", "identity:advisor-eligibility:read")
    .issueTime(java.util.Date.from(now))
    .expirationTime(java.util.Date.from(now.plusSeconds(60)))
    .build();
var header = new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256)
    .type(new com.nimbusds.jose.JOSEObjectType("sanigest-service+jwt"))
    .build();
var assertion = new com.nimbusds.jwt.SignedJWT(header, claims);
assertion.sign(companySigner);
String bearer = assertion.serialize();
```

Cada nueva solicitud puede firmar un JWT fresco. El TTL máximo es de 120 s.
Los relojes de ambos servicios deben estar sincronizados. El JWT es bearer y
puede reutilizarse hasta su expiración. La revocación de emergencia requiere
retirar el par de variables o reemplazar la clave pública y reiniciar todas
las réplicas de Identity. Para rotar, coordina el cambio del firmante en
Company y el despliegue de la nueva clave pública en Identity. Se acepta una
clave a la vez y las solicitudes durante la transición pueden recibir `401`.

La consulta está disponible en `local` y `test`, donde Identity tiene
persistencia. En Compose, inyecta ambas variables en el contenedor Identity
y monta el PEM público de solo lectura. El despliegue fuera de esos perfiles
requiere habilitar la persistencia de Identity y aprovisionar credenciales
técnicas. Company conserva la decisión de concesión empresarial.
