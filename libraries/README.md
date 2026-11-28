# Librerías compartidas

| Módulo | Estado | Límite |
| --- | --- | --- |
| [common-security](common-security) | En reactor | Validación JWT. Consumidores Gateway, Identity, Company, Catalog, Sales e Inventory. |
| [common-company-access](common-company-access) | En reactor | Protocolo técnico Company, Catalog, Sales e Inventory. S2-454 evoluciona el selector. |
| [common-web](common-web) | En reactor | Errores RFC 9457 y paginación genérica en Company y Catalog. |
| [common-events](common-events) | En reactor | Contratos versionados de pagos confirmados y reversos. |
| common-cache | Candidato | TTL, convenciones de claves y métricas sin política de negocio. |
| common-messaging | Candidato | Adaptación Kafka, instrumentación y utilidades técnicas de publicación/consumo. |
| common-observability | Candidato | Correlación, métricas y configuración técnica compartida. |
| common-test-support | Candidato | Fixtures y arranque de infraestructura reutilizable para pruebas. |

Los paquetes de cada librería reflejan sus responsabilidades. Por ejemplo,
`common-events` define el contrato de pagos en `domain/model/payment` y
`domain/enums/payment`, el puerto de escritura en
`application/port/out/persistence` y los adaptadores de outbox en
`infrastructure/persistence/outbox` e `infrastructure/messaging/outbox`.
Las pruebas unitarias reflejan el paquete del código que ejercitan.

Extraer un módulo cuando existan al menos dos consumidores concretos, un
contrato estable y una reducción demostrable de duplicación técnica. Mantener
la propiedad de reglas y datos en cada servicio. Una librería no distribuye
entidades JPA, repositorios, agregados, reglas comerciales, servicios de
negocio ni modelos internos de persistencia. El
[ADR-002](../docs/adr/ADR-002-evolucion-arquitectonica-multiempresa-inventario-finanzas.md)
conserva estos límites del ADR-001.
