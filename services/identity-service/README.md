# Identity refresh tokens (S2-386)

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
