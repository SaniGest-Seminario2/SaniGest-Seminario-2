# API Gateway · S2-410

Spring Cloud Gateway Server WebFlux. Usa prefijos explícitos y versionados
como /api/v1/<recurso> y destinos internos configurados por el operador. Los
servicios propietarios aplican la autorización empresarial. En despliegue,
solo el Gateway recibe tráfico público.

Desde la raíz, con Java 21 y Maven:

    mvn -pl services/api-gateway verify
    SPRING_PROFILES_ACTIVE=local SERVER_PORT=8080 GATEWAY_TEST_BACKEND_URL=http://127.0.0.1:8081 \
      java -jar services/api-gateway/target/api-gateway-0.1.0-SNAPSHOT.jar

GET /api/v1/gateway/ping se reenvía a GET /ping solo en los perfiles local y
test. El puerto predeterminado es 8080 y en test es 0. Health, liveness y
readiness se exponen bajo /actuator/health e informan del estado del Gateway.

CORS permite GET y preflight desde http://localhost:3000 y
http://127.0.0.1:3000 sin credenciales. El Gateway genera X-Correlation-Id
para cada solicitud y lo devuelve al cliente. La ruta diagnóstica excluye
credenciales y contexto de trazas entrante del reenvío.

Las rutas protegidas futuras devolverán 401 por autenticación ausente o
inválida y 403 por autorización insuficiente. El preflight rechazado devuelve
403 y las rutas desconocidas devuelven 404. Este skeleton carece de validación JWT.
