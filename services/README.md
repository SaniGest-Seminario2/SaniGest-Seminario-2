# Servicios

| Módulo | Responsabilidad | Datos propios |
| --- | --- | --- |
| [API Gateway](api-gateway/README.md) | Entrada pública, validación de acceso y enrutamiento HTTP | — |
| [Identity](identity-service/README.md) | Registro, identidad, autenticación y sesiones | `sanigest_identity` |
| [Company](company-service/README.md) | Empresas, membresías, invitaciones y auditoría | `sanigest_company` |
| [Catalog](catalog-service/README.md) | Maestros de clientes, proveedores, productos y servicios | `sanigest_catalog` |

Cada aplicación tiene su propio POM, configuración de Spring y pruebas.
Company, Identity y Catalog organizan el código en `api`, `application`,
`domain` e `infrastructure`, mantienen sus propias migraciones y tienen
imágenes en el perfil `apps` de Docker Compose. Gateway organiza su código
en enrutamiento, seguridad y observabilidad, y se ejecuta localmente con Maven.

La [guía para añadir un servicio](../docs/development/new-service.md) describe
cómo incorporarlo al reactor y a CI. Los módulos previstos están en el
[mapa de evolución](../docs/roadmap/README.md).
