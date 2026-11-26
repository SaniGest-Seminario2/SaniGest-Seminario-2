# Matriz UI → API v0.9

Base main `454eb69`. Rutas relativas a `/api/v1`. **Vigente** indica ruta presente, **Evolución** indica cambio sobre una ruta presente y **Pendiente** indica objetivo aún sin ruta en main. Las propuestas de ruta quedan sujetas al ticket propietario. [Contrato por pantallas](../SaniGest_T1001_Contrato_Funcional_v0.9.md) · [Inventario y plan](s2-472-endpoint-lifecycle.md).

| Pantalla y capacidad | Método y ruta | Propietario | Estado | Jira |
| --- | --- | --- | --- | --- |
| Registro, sesión y perfil | POST /auth/{register,login,refresh,logout}; GET /auth/me | Identity | Vigente | S2-474, S2-416 |
| Onboarding y selección por pestaña | GET/POST /companies; X-Company-Id | Company/Gateway | Vigente | S2-453, S2-454, S2-474, S2-421 |
| Perfil empresa | GET/PATCH /companies/{companyId} | Company | Vigente | S2-454, S2-471, S2-474 |
| Equipo paginado | GET /companies/{companyId}/users?view=page | Company | Evolución | S2-474 |
| Equipo sin paginar | GET /companies/{companyId}/users/all | Company | Pendiente | S2-474 |
| Equipo e incorporación | POST/GET /companies/{companyId}/invitations; DELETE /companies/{companyId}/invitations/{invitationId}; POST /invitations/{token}/accept | Company | Evolución entrega/alta; gestión vigente | S2-455, S2-474 |
| Equipo, roles y desactivación | PATCH /companies/{companyId}/members/{userId}/{role,status}; DELETE /companies/{companyId}/members/{userId} | Company | Vigente | S2-455, S2-474 |
| Opciones fijas y filtros | GET /companies/{companyId}/options; GET /catalog/options; GET /inventory/options; GET /sales/options | Company/Catalog/Inventory/Sales | Vigente | S2-473 |
| Opciones de Expense y Finance | Rutas por definir en sus respectivos servicios | Expense/Finance | Pendiente | S2-342, S2-349, S2-432 |
| Selectores BD | GET /clients/options; GET /suppliers/options; GET /catalog-items/options; GET /warehouses/options | Catalog/Inventory | Vigente | S2-473 |
| Selector de cuentas | GET /accounts/options | Finance | Pendiente | S2-467 |
| Clientes, ficha y desactivación | GET/POST /clients; GET/PATCH /clients/{id}; PATCH /clients/{id}/status | Catalog | Evolución formulario | S2-475, S2-418 |
| Clientes sin paginar | GET /clients/all | Catalog | Pendiente | S2-475 |
| Referencias y categorías | GET /clients/reference-data/{countries,provinces,municipalities,receipt-types}; GET /{clients,suppliers,catalog-items}/categories; GET /catalog-items/{units,tax-classifications} | Catalog | Vigente | S2-473, S2-475–477 |
| Clientes, saldo, indicadores y ficha financiera | Composición en GET /clients y /clients/all; GET /clients/{id}/{financial-summary,statement} (propuestas); resumen ruta pendiente | Catalog/Sales | Pendiente | S2-457, S2-418 |
| Proveedores, ficha y desactivación | GET/POST /suppliers; GET/PATCH /suppliers/{id}; PATCH /suppliers/{id}/status | Catalog | Evolución formulario | S2-476, S2-419 |
| Proveedores sin paginar y saldo | GET /suppliers/all; composición con Expense para tabla y ficha (contrato de lote pendiente) | Catalog/Expense | Pendiente | S2-476, S2-346 |
| Ofertas de proveedor | GET/PUT /suppliers/{id}/items; GET /catalog-items/{id}/suppliers | Catalog | Evolución | S2-476, S2-477 |
| Productos y servicios | GET/POST /catalog-items; GET/PATCH /catalog-items/{id}; PATCH /catalog-items/{id}/status; GET /catalog-items/summary; GET /catalog-items/{id}/history | Catalog | Evolución formulario/resumen; filtros e historial vigentes | S2-471, S2-477, S2-417 |
| Productos y servicios sin paginar | GET /catalog-items/all | Catalog | Pendiente | S2-477 |
| Inventario, almacenes y existencias | GET/POST /warehouses; GET/PATCH /warehouses/{id}; GET/PUT /inventory/stock/{itemId}/{warehouseId}; GET /inventory/{stock,summary,movements}; POST /inventory/adjustments | Inventory | Vigente | S2-460, S2-471, S2-477 |
| Ventas y anulación | GET/POST /sales; GET /sales/{id}; POST /sales/{id}/cancel | Sales | Evolución | S2-461, S2-463, S2-465, S2-422 |
| Ventas sin paginar e indicadores | GET /sales/all; GET /sales/summary | Sales | Pendiente | S2-463, S2-422 |
| Factura desde venta | POST /sales/{saleId}/invoices | Sales | Vigente | S2-335, S2-462, S2-420 |
| Facturación interna | GET/POST /invoices; GET /invoices/all; GET /invoices/{id}; POST /invoices/{id}/cancel | Sales | Pendiente | S2-336, S2-377, S2-378, S2-462, S2-420 |
| Cartera CxC, ficha e historial | GET /receivables; GET /receivables/all; GET /receivables/{id}; GET /receivables/{id}/payments | Sales | Ficha vigente; resto pendiente | S2-337, S2-338, S2-340, S2-424 |
| Caja y cobros | GET /invoices/collectible; GET /invoices/collectible/all; POST /invoices/{invoiceId}/payments (adaptador CxC condicionado); historial por turno (ruta pendiente); POST /cash-sessions; GET /cash-sessions/active; GET /cash-sessions/{id}/summary; POST /cash-sessions/{id}/close | Sales/Finance | Pendiente | S2-336, S2-339, S2-340, S2-464, S2-467, S2-479 |
| Compras y gastos | GET/POST /expenses; GET /expenses/all; GET/PATCH /expenses/{id}; GET /expenses/summary; POST /expenses/{id}/cancel | Expense | Pendiente | S2-342, S2-343, S2-344, S2-352, S2-426 |
| Compras, recepción y compensación | POST /expenses/{id}/receipts; POST /expenses/{id}/receipts/{receiptId}/reversals (propuesta) | Expense/Inventory | Pendiente | S2-478 |
| Cartera CxP, ficha y pagos | GET /payables; GET /payables/all; GET /payables/{id}; GET/POST /payables/{id}/payments | Expense | Pendiente | S2-346, S2-347, S2-348, S2-425 |
| Reversos financieros | Subrecursos de reversión de pagos, rutas a cerrar por propietario | Sales/Expense | Pendiente | S2-341, S2-348 |
| Saldos iniciales y adjuntos | Rutas por propietario y pantalla aprobada | Sales/Expense/Finance | Pendiente | S2-337, S2-345, S2-430, S2-466 |
| Cuentas y transferencias | GET/POST /financial-accounts; GET/PATCH /financial-accounts/{id}; POST /transfers | Finance | Pendiente | S2-467 |
| Ingresos, egresos y CSV | GET/POST /cash-movements; GET /cash-movements/all; GET /cash-movements/{id}; GET /cash-movements/summary; GET /cash-movements/export | Finance | Pendiente | S2-349–351, S2-467, S2-469, S2-427 |
| Dashboard y reportes | GET /dashboard/{summary,recent-activity,monthly-activity}; GET /reports/financial-summary | Finance | Pendiente | S2-353–356, S2-468, S2-428, S2-429 |
| Auditoría por dominio | GET /companies/{companyId}/audit-log; consultas de otros dominios con ruta pendiente | Company y propietarios de efectos | Company vigente; extensión pendiente | S2-412, S2-341, S2-480 |

Facturación fiscal, UI de asesores y analítica predictiva quedan fuera del alcance de pantallas v0.9. Conservar las concesiones existentes y sus controles. S2-423 alinea diseño con este contrato y S2-431 valida la integración frontend, OpenAPI y Postman.

Referencias Jira de la revisión: [S2-472][472] · [S2-473][473] · [S2-474][474] · [S2-475][475] · [S2-476][476] · [S2-477][477] · [S2-478][478] · [S2-479][479] · [S2-480][480].

[472]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-472

[473]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-473

[474]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-474

[475]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-475

[476]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-476

[477]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-477

[478]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-478

[479]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-479

[480]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-480
