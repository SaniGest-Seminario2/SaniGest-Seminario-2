# Módulos previstos

| Servicio | Ticket | Alcance |
| --- | --- | --- |
| [Catalog](catalog-service.md) | S2-318 | Clientes, proveedores y catálogo |
| [Sales](sales-service.md) | S2-331 | Venta, facturación y cuentas por cobrar |
| [Expense](expense-service.md) | S2-342 | Compras, gastos y cuentas por pagar |
| [Finance](finance-service.md) | S2-349 | Caja, dashboard y reportes |

Estos módulos cuentan con responsabilidades definidas en el
[contrato funcional](../SaniGest_T1001_Contrato_Funcional_v0.7.md).
Los tickets respectivos incorporarán el código ejecutable al reactor Maven.
La auditoría pertenece al servicio que ejecuta la operación y el MVP incorpora
las notificaciones en los flujos de cada servicio.
