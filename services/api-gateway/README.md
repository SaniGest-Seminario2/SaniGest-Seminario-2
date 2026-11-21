# API Gateway

Spring Cloud Gateway Server WebFlux ofrece una única base pública para el
frontend. Los servicios reciben las rutas versionadas originales por HTTP y
conservan su autenticación, autorización y contratos de respuesta.

| Rutas | Destino |
| --- | --- |
| `/api/v1/auth/**`, `/api/v1/users/**` | `IDENTITY_SERVICE_URL` |
| `/api/v1/companies/**` | `COMPANY_SERVICE_URL` |
| `/api/v1/clients/**`, `/api/v1/suppliers/**`, `/api/v1/catalog-items/**` | `CATALOG_SERVICE_URL` |

Configura cada destino como un origen HTTP(S), con host y puerto si aplica.
Cada grupo de rutas se habilita al configurar su variable. Define Identity y
Company para el despliegue del frontend y habilita Catalog cuando exista un
servicio ejecutable. Las rutas sin destino devuelven 404 tras autenticar la petición. El Gateway reenvía el
método, la ruta, la query, el body y los headers de aplicación, incluido
`Authorization`. Los clientes reciben los estados y respuestas del backend.
Los fallos de conexión y el timeout de respuesta producen 503 y 504,
respectivamente. Los límites iniciales son 500 ms de conexión y 2 s de respuesta.

Define `GATEWAY_CORS_ALLOWED_ORIGINS` como una lista separada por comas de
orígenes exactos con esquema, host y puerto cuando corresponda. Con la lista
vacía se rechazan solicitudes cross-origin. Se permiten GET, POST, PUT, PATCH,
DELETE y preflight OPTIONS con `Accept`, `Authorization`, `Content-Type` e
`Idempotency-Key`. El frontend puede leer `X-Correlation-Id`.

El Gateway genera `X-Correlation-Id`, `X-Trace-Id` y `traceparent` por
solicitud y descarta `tracestate` y `baggage` entrantes. Mantén desactivado
el wiretap HTTP para evitar registrar headers y cuerpos con credenciales.

Configura `JWT_ISSUER`, `JWT_AUDIENCE` y `JWT_PUBLIC_KEY_LOCATION` según el
[contrato de seguridad](../../docs/seguridad-s2-317.md). El Gateway valida el
token antes del reenvío. Los POST de login, registro, refresh y logout aceptan
peticiones sin access token. Refresh y logout usan el refresh token.

Desde la raíz del repositorio:

```sh
mvn -pl services/api-gateway -am verify
SPRING_PROFILES_ACTIVE=local SERVER_PORT=8080 \
  IDENTITY_SERVICE_URL=http://127.0.0.1:8082 \
  COMPANY_SERVICE_URL=http://127.0.0.1:8083 \
  GATEWAY_CORS_ALLOWED_ORIGINS=http://localhost:3000 \
  java -jar services/api-gateway/target/api-gateway-0.1.0-SNAPSHOT.jar
```

`GET /api/v1/gateway/ping` conserva su reenvío diagnóstico a `GET /ping` en
los perfiles `local` y `test` con un JWT válido. Su destino se configura con
`GATEWAY_TEST_BACKEND_URL` (por defecto `http://127.0.0.1:8081`) y el
reenvío omite credenciales y contexto de trazas. El perfil `test` usa un
puerto aleatorio por defecto. Actuator ofrece `/actuator/health` y sus
subrutas `/liveness` y `/readiness` para comprobar el Gateway.

En AWS, usa un ALB público con HTTPS como entrada al Gateway y configura
`/actuator/health/readiness` como health check del target group. Ejecuta los
servicios en ECS/Fargate con IP privada, usa DNS interno para los destinos y
limita el ingreso a sus puertos HTTP al security group del Gateway. Autoriza
también el acceso de Company a Identity para consultar la cuenta activa. Configura
el origen HTTPS del frontend y usa reglas del ALB que publiquen las rutas API.
`GET /api/v1/companies/me` conserva las decisiones de autenticación y acceso
de Company, incluida la respuesta 401 ante un JWT inválido.
