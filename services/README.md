# Servicios

| Módulo | Responsabilidad | Datos propios |
| --- | --- | --- |
| [API Gateway](api-gateway/README.md) | Entrada pública, validación de acceso y enrutamiento HTTP | — |
| [Identity](identity-service/README.md) | Registro, identidad, autenticación y sesiones | `sanigest_identity` |
| [Company](company-service/README.md) | Empresas, membresías, invitaciones y auditoría | `sanigest_company` |

Cada aplicación tiene su propio POM, configuración de Spring, migraciones y
pruebas. Gateway se ejecuta localmente con Maven. Company e Identity también
tienen imágenes en el perfil `apps` de Docker Compose.

La [guía para añadir un servicio](../docs/development/new-service.md) describe
cómo incorporarlo al reactor y a CI. Los módulos previstos están en el
[mapa de evolución](../docs/roadmap/README.md).
