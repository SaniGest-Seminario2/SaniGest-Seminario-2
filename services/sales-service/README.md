# Sales Service

Sales es dueño de las ventas y sus líneas en PostgreSQL. S2-331 entrega el
modelo, la persistencia y los endpoints de salud. Las operaciones HTTP y su
documentación OpenAPI se incorporarán con los casos de uso correspondientes.
Consulta el [contrato funcional v0.7](../../docs/SaniGest_T1001_Contrato_Funcional_v0.7.md)
y el [ADR-001](../../docs/adr/ADR-001-propiedad-datos-comunicacion-servicios.md).

## Arquitectura y datos

- `domain` conserva los agregados y reglas de venta en Java.
- `application.port.out.persistence` define la escritura atómica del agregado
  y la consulta de detalle por empresa e identificador.
- `infrastructure.persistence.jpa` mapea el agregado a sus tablas privadas.
  `infrastructure.security` configura la validación de JWT y el acceso a salud.

Sales usa la base y el rol `sanigest_sales`, provisionados por el bootstrap de
PostgreSQL. Flyway crea el esquema `sales` y administra las migraciones.
Hibernate valida el esquema al arrancar y Open Session in View está
deshabilitado. La única FK conecta una línea con su venta.
Los UUID externos y los nombres, descripciones y precios efectivos se guardan
como snapshots de la operación.

La fecha comercial es `LocalDate` y se guarda como `DATE`. La modalidad es
`CASH` o `CREDIT`. El ciclo de vida inicial usa `REGISTERED` y `CANCELLED`.
Los estados de cobro del contrato se derivarán de cobros verificados. La
validación de elegibilidad y la auditoría de cancelación corresponden a S2-334.

Los precios, subtotales y totales son `BigDecimal` en DOP con dos decimales,
hasta `99999999999999999.99`. La cantidad positiva admite hasta tres decimales
y debe ser inferior a `1000000000000`. El subtotal es el producto exacto
de precio y cantidad. Se rechazan las fracciones de centavo y los importes fuera
de rango, con `RoundingMode.UNNECESSARY` al normalizar. PostgreSQL usa
`NUMERIC` con controles de escala y rango para rechazar entradas inválidas
antes de que puedan redondearse. La suma de líneas se valida en el agregado,
que el adaptador inserta en una única transacción local.

## Arranque local

Configura la contraseña en `.env` y genera la clave pública según la
[configuración JWT](../../docs/seguridad-s2-317.md). Desde la raíz del repo:

```sh
docker compose --profile apps up -d --build --wait sales-service
curl -fsS http://localhost:8084/actuator/health
```

Para ejecutar el JAR localmente, levanta PostgreSQL con
`docker compose --profile infra up -d --wait postgres` y exporta las variables
necesarias antes de ejecutar:

```sh
mvn -B -pl services/sales-service -am package
java -jar services/sales-service/target/sales-service-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

| Variable | Configuración |
| --- | --- |
| `SALES_DB_HOST` | Obligatoria en el perfil base. El perfil `local` usa `localhost` por defecto. |
| `SALES_DB_PORT` | Puerto PostgreSQL. El perfil `local` admite `POSTGRES_PORT` como alternativa. Predeterminado `5432`. |
| `SALES_DB_PASSWORD` | Contraseña del rol `sanigest_sales`. |
| `SALES_SERVICE_PORT` | Puerto local o publicado por Compose. Predeterminado `8084`. |
| `SERVER_PORT` | Sobrescribe el puerto del proceso Spring Boot. |
| `JWT_ISSUER`, `JWT_AUDIENCE` | Obligatorios en el perfil base. En `local` se usan `sanigest` y `sanigest-api` por defecto. |
| `JWT_PUBLIC_KEY_LOCATION` | Ubicación de la clave pública RSA en PEM, por ejemplo `file:/ruta/jwt-public.pem`. |

La salud y sus sondas `liveness` y `readiness` son públicas. La respuesta muestra
el estado y, en la ruta raíz, los nombres de las sondas. Readiness comprueba
PostgreSQL. Las demás solicitudes se deniegan por
defecto. La seguridad reutiliza `common-security` para verificar firma, issuer,
audience y expiración.

## Pruebas

```sh
mvn -B -pl services/sales-service -am test
mvn -B -pl services/sales-service -am verify
mvn -B -pl services/sales-service -am -Psmoke verify
mvn -B -pl services/sales-service -am -Pregression verify
```

El perfil de pruebas usa PostgreSQL 17 en Testcontainers y un puerto HTTP
dinámico. V1 se valida desde una base limpia y se ejecuta Flyway por segunda
vez para comprobar el historial, checksum y ausencia de migraciones pendientes.

Finance recibirá eventos financieros al incorporar cobros, mediante outbox y
Kafka conforme al ADR-001.

## Integración con Catalog

`application.port.out.integration.CatalogClient` entrega snapshots de clientes
y artículos elegibles para una venta. El adaptador de
`infrastructure.integration.catalog` ejecuta dos GET mediante una interfaz
declarativa de Spring y `RestClient`.

| Ruta en Catalog | Snapshot de Sales |
| --- | --- |
| `GET /api/v1/clients/{id}` | UUID, nombre y estado `ACTIVE`. |
| `GET /api/v1/catalog-items/{id}` | UUID, nombre, tipo `PRODUCT` o `SERVICE`, descripción nullable, precio exacto con escala 2, moneda `DOP` y estado activo. |

Los DTO de transporte son privados de la integración. El cliente valida
identificadores, estructura, estados e importes antes de construir el snapshot.
S2-332 consumirá estos valores para registrar la venta y persistir el importe y
los textos vigentes al momento de vender. Las ventas históricas se consultan
desde Sales.

| Variable | Configuración |
| --- | --- |
| `SALES_CATALOG_ORIGIN` | Origen dedicado de Catalog, por ejemplo `https://catalog.internal`. El perfil `local` y las pruebas admiten HTTP. Compose apunta a `http://catalog-service:8080` por defecto. Si está vacío, las consultas fallan con `NOT_CONFIGURED`. |
| `SALES_CATALOG_CONNECT_TIMEOUT` | Timeout de conexión. Predeterminado `500ms`. |
| `SALES_CATALOG_RESPONSE_TIMEOUT` | Timeout de respuesta. Predeterminado `2s`. |

Sales propaga el JWT de sesión validado por Spring Security al origen
configurado. Sales y Catalog usan la firma, el emisor y la audiencia del
contrato compartido. El cliente bloquea redirecciones y carece de reintentos
configurados. Los GET de detalle requieren `CATALOG_READ`. Catalog resuelve
la membresía vigente contra Company en cada consulta y busca por la empresa
autorizada y el UUID. Habilita la integración de Catalog con Company para
autorizar estas consultas según su [configuración](../../libraries/common-company-access/README.md). Los headers de empresa enviados por consumidores se ignoran. El contrato v0.7 asigna como máximo una empresa a cada usuario
regular. La autorización de escritura de Sales en Company corresponde a
S2-373 y el caso de uso de S2-332 deberá usar esa empresa verificada.
Micrometer observa las llamadas HTTP y se envían `X-Trace-Id` y `traceparent`
para correlación. Las excepciones omiten cuerpos de respuesta y credenciales.

| Fallo | Traducción en Application |
| --- | --- |
| Sesión ausente o vencida, 401, 403 | `CatalogAccessDeniedException` con motivos distintos. |
| 400, 404, recurso inactivo o JSON inválido | `CatalogResourceInvalidException`. Un 404 puede indicar un recurso inexistente o fuera de la empresa autorizada. |
| 429, 5xx, fallo de red o timeout | `CatalogUnavailableException`. |

Catalog puede estar indisponible durante el arranque de Sales. La salud de Sales
se comprueba con sus propias dependencias y una consulta remota fallida termina
sin snapshot. La prueba transaccional de registro corresponde a S2-332.

```sh
mvn -B -pl services/sales-service -am -Dtest=CatalogHttpClientTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -B -pl services/sales-service,services/catalog-service -am verify
```

WireMock cubre el tráfico saliente, contratos y fallos de Catalog. La
regresión de Catalog usa su configuración real de Spring Security, JWT
firmados, PostgreSQL y la autoridad Company simulada para comprobar
aislamiento y revocación en los dos GET de detalle.
