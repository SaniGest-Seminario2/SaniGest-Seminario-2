# Servicios

| Módulo | Estado del repositorio | Responsabilidad y datos propios |
| --- | --- | --- |
| [API Gateway](api-gateway/README.md) | Maven; enruta Identity, Company y Catalog | Entrada pública y validación de borde, sin BD |
| [Identity](identity-service/README.md) | Reactor y Compose | Identidad, perfil, autenticación, sesiones. `sanigest_identity` |
| [Company](company-service/README.md) | Reactor y Compose | Empresas, membresías, invitaciones, permisos y auditoría. `sanigest_company` |
| [Catalog](catalog-service/README.md) | Reactor y Compose | Maestros comerciales y relaciones de suministro objetivo. `sanigest_catalog` |
| [Sales](sales-service/README.md) | Reactor y Compose | Ventas, facturas internas y obligaciones; cobros objetivo. `sanigest_sales` |
| [Inventory](../docs/roadmap/inventory-service.md) | Pendiente | Almacén, stock, reservas y kardex. `sanigest_inventory` propuesta |
| [Expense](../docs/roadmap/expense-service.md) | Pendiente; base local reservada | Compras, gastos, recepciones, obligaciones y pagos. `sanigest_expense` |
| [Finance](../docs/roadmap/finance-service.md) | Pendiente; base local reservada | Cuentas, caja, movimientos, transferencias y proyecciones. `sanigest_finance` |

Los servicios de negocio organizan su código en API, aplicación, dominio e
infraestructura y mantienen migraciones independientes. Consulta la
[guía de incorporación](../docs/development/new-service.md), el
[mapa de evolución](../docs/roadmap/README.md) y las
[rutas verificadas y objetivo](../docs/traceability/ui-backend-matrix-v0.8.md).
