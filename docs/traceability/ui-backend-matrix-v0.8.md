# Trazabilidad UI → backend v0.8

Rutas relativas a `/api/v1`, salvo la ruta interna indicada. **I** implementado, **E** existente pero requiere evolución, **P** pendiente, **F** fuera del MVP. «→» separa ruta vigente de ruta objetivo. El estado corresponde al código inspeccionado, no al estado Jira. Las rutas de Sales están en sus controladores; Gateway enruta Identity, Company, Catalog, Sales e Inventory. La [especificación](../SaniGest_T1001_Contrato_Funcional_v0.8.md) y el [ADR-002](../adr/ADR-002-evolucion-arquitectonica-multiempresa-inventario-finanzas.md) aplican a todas las filas objetivo.

| Pantalla | Acción | Recurso API | Propietario | Dependencias | Evento objetivo | Jira | Estado |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Acceso y perfil | Registrar, iniciar, renovar, cerrar, consultar | POST /auth/{register,login,refresh,logout}; GET /auth/me | Identity | Gateway | — | S2-386, S2-416 | I |
| Onboarding | Crear empresa y OWNER (alta múltiple activable en Company) | POST /companies | Company | Identity | — | S2-308, S2-453 | E |
| Selector | Listar empresas operativas de la cuenta REGULAR | GET /companies | Company | Identity | — | S2-453 | E |
| Selector | Seleccionar empresa por petición | X-Company-Id por petición | Company | Identity, Gateway | — | S2-454 | I |
| Contexto | Autorizar ruta de negocio | GET /internal/v2/company-access | Company | Identity, common-company-access | — | S2-317, S2-330, S2-454 | I |
| Empresa | Perfil y auditoría | GET/PATCH /companies/{companyId}; GET /companies/{companyId}/audit-log; compatibilidad /companies/me | Company | Identity | — | S2-309, S2-454 | I |
| Equipo | Crear, listar y revocar invitaciones pendientes, aceptar con correo verificado | POST/GET /companies/{companyId}/invitations; DELETE /companies/{companyId}/invitations/{invitationId}; POST /invitations/{token}/accept | Company | Identity | Verificación real de correo y entrega productiva pendientes | S2-311, S2-454, S2-455 | E |
| Equipo | Listar miembros con búsqueda, filtros y paginación | GET /companies/{companyId}/users?view=page, array compatible sin view | Company | Identity, directorio interno | — | S2-311, S2-454, S2-455 | I |
| Equipo | Cambiar rol, suspender, reactivar o revocar | PATCH /companies/{companyId}/members/{userId}/{role,status}; DELETE /companies/{companyId}/members/{userId} | Company | Identity | — | S2-312, S2-454, S2-455 | I |
| Acceso delegado | Listar y administrar concesión | GET /advisor/companies; GET/PUT/DELETE /companies/{companyId}/advisor-access | Company | Identity | — | S2-454 | I |
| Clientes | Listar, filtrar, crear, editar, estado y seleccionar activos | GET/POST /clients; GET/PATCH /clients/{id}; PATCH /clients/{id}/status | Catalog | Company | — | S2-319, S2-320, S2-321, S2-456 | I |
| Clientes | Consultar referencias y gestionar categorías | GET/POST /clients/categories; PUT /clients/categories/{id}; GET /clients/reference-data/{countries,provinces,municipalities,receipt-types} | Catalog | Company | — | S2-456 | I |
| Ficha cliente | Métricas, facturas, cobros y estado de cuenta | GET /clients/{id}/{financial-summary,statement} | Sales | Company, Catalog | — | S2-457 | P |
| Proveedores | Listar, crear, editar, estado | GET/POST /suppliers; GET/PATCH /suppliers/{id}; PATCH /suppliers/{id}/status | Catalog | Company | — | S2-419, S2-458 | I |
| Proveedores | Administrar categorías | GET/POST /suppliers/categories; PUT /suppliers/categories/{id} | Catalog | Company | — | S2-458 | I |
| Ficha proveedor | Relacionar artículos y costo | GET/PUT /suppliers/{id}/items; GET /catalog-items/{id}/suppliers | Catalog | Company | — | S2-458 | I |
| Ficha proveedor | Saldo y pagos | GET /suppliers/{id}/payable-summary; GET /payables?supplierId=… | Expense | Company, Catalog | — | S2-346, S2-348, S2-458 | P |
| Productos y servicios | Buscar, crear, editar, estado, precios e ITBIS | GET/POST /catalog-items; GET/PATCH /catalog-items/{id}; PATCH /catalog-items/{id}/status; GET /catalog-items/{categories,units,tax-classifications,summary}; GET /catalog-items/{id}/history | Catalog | Company; Inventory para stock (S2-460); Sales para activación de cuatro decimales (S2-461) | — | S2-417, S2-459 | I |
| Inventario | Almacenes, configuración, disponibilidad, kardex y ajustes | GET/POST /warehouses; GET/PATCH /warehouses/{id}; GET /inventory/{stock,summary,movements}; GET/PUT /inventory/stock/{itemId}/{warehouseId}; POST /inventory/adjustments | Inventory | Company, Catalog | — | S2-460 | I |
| Ventas | Crear, listar, detalle y cancelar | GET/POST /sales; GET /sales/{id}; POST /sales/{id}/cancel | Sales | Company, Catalog, Inventory | Venta registrada/anulada | S2-332 a S2-334, S2-461, S2-463, S2-465 | E |
| Facturación | Emitir desde venta | POST /sales/{saleId}/invoices | Sales | Company | Factura interna emitida | S2-335, S2-420, S2-462 | E |
| Facturación | Formulario, listado, ficha | GET/POST /invoices; GET /invoices/{id} | Sales | Company, Catalog, Inventory | Venta y factura registradas según origen | S2-336, S2-420, S2-462 | P |
| Facturación fiscal | Emitir NCF/e-CF oficial | Alcance fiscal independiente | Por definir | Validación DGII | — | Alcance separado en S2-452 | F |
| Caja | Apertura, admisión, historial y cierre | /cash-sessions y subrecursos | Finance | Company, Sales | Resultado de intento y aplicaciones | S2-464 | P |
| Cobros | Buscar facturas cobrables y cobrar en uno o varios medios | GET /invoices; GET/POST /receivables/{id}/payments | Sales | Company, Finance | ReceivablePaymentRegisteredV1 | S2-336, S2-339, S2-340, S2-378, S2-463, S2-464 | P |
| Compras y gastos | Registrar, confirmar, listar, anular | /expenses y subrecursos | Expense | Company, Catalog | Compra/gasto registrado o anulado | S2-342 a S2-345, S2-352 | P |
| Recepciones | Recibir por línea/almacén, revertir | /expenses/{id}/receipts y reversión | Expense | Company, Inventory | Recepción, reversión y resultado | S2-465 | P |
| Cuentas por cobrar | Detalle de obligación vigente | GET /receivables/{id} | Sales | Company | — | S2-337, S2-463 | E |
| Cuentas por cobrar | Cartera, inicial, historial y reversión | /receivables y subrecursos /payments y /reversals | Sales | Company, Finance | Saldo, cobro y reversión | S2-338 a S2-341 | P |
| Cuentas por pagar | Cartera, inicial, abono parcial o total, historial, reversión | /payables y subrecursos /payments y /reversals | Expense | Company, Finance | Saldo, PayablePaymentRegisteredV1 y reversión | S2-345 a S2-348 | P |
| Ingresos y egresos | Gestionar cuentas y transferir | /financial-accounts; /transfers | Finance | Company | Apuntes internos correlacionados | S2-467 | P |
| Movimientos | Crear, buscar, detalle, CSV | GET/POST /cash-movements; GET /cash-movements/{id}; GET /cash-movements/export | Finance | Company, Sales, Expense | Consumo cobro/pago confirmado | S2-349 a S2-351, S2-358, S2-359, S2-469 | P |
| Soportes | Cargar, validar, consultar adjunto | /expenses/{id}/attachments; /cash-movements/{id}/attachments | Expense / Finance | Company, almacenamiento privado | — | S2-466 | P |
| Inicio | Resumen, series, actividad, facturas recientes | /dashboard/{summary,activity,recent-activity,recent-invoices} | Finance | Company, Sales, Expense | Proyecciones documentales y de cartera | S2-353, S2-354, S2-379, S2-387, S2-468 | P |
| Reportes | Indicadores y tendencias al corte | /reports/{summary,monthly-activity,receivables-payables,operations} | Finance | Company, Sales, Expense | Proyecciones y efectos efectivos | S2-355, S2-356, S2-387, S2-468 | P |
| Analítica predictiva | Evaluar demanda, anomalías y tendencias | Spike, sin API MVP | Por evaluar | Datos autorizados y baseline | Por evaluar | S2-470 | F |

Los listados de clientes y proveedores admiten search, status, page, size, categoryId, paymentTerms y sort. Clientes admite clientType y proveedores supplyType. Artículos admite search, type, active, page y size. En Sales, el GET vigente de ventas admite search, status, from, to, page y size. El filtro de elegibilidad de factura de v0.7 sigue siendo propuesta. La venta actual usa precio del catálogo y cantidades, y su cuenta por cobrar nace en CREDIT. La cobertura de CASH, filtros ampliados y cobros se aplica según el contrato objetivo, preservando histórico y DTO publicados.

## Secuencia y verificación en Jira

[S2-452][452] define este contrato. [S2-453][453] y [S2-454][454] deben coordinar activación, antes de exponer nuevas capacidades por empresa. [S2-455][455] amplía el equipo. La ficha comercial evoluciona en [S2-456][456], [S2-457][457], [S2-458][458] y [S2-459][459]. Inventario y flujos transaccionales corresponden a [S2-460][460], [S2-461][461], [S2-462][462], [S2-463][463], [S2-464][464] y [S2-465][465]. Soportes, cuentas, lectura y CSV corresponden a [S2-466][466], [S2-467][467], [S2-468][468] y [S2-469][469]. [S2-470][470] es una evaluación posterior. S2-384 versiona eventos técnicos, S2-357 opera Kafka, S2-360 cubre resiliencia, S2-361/363/365 verifican flujo, aislamiento y contratos, y S2-423 alinea pantallas con el contrato. Los tickets Done sirven como antecedente de regresión.

[452]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-452
[453]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-453
[454]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-454
[455]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-455
[456]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-456
[457]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-457
[458]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-458
[459]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-459
[460]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-460
[461]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-461
[462]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-462
[463]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-463
[464]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-464
[465]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-465
[466]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-466
[467]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-467
[468]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-468
[469]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-469
[470]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-470

Los selectores, permisos y mecanismos de autorización por ruta se detallan en la [matriz de contexto empresarial](../development/company-context.md).

El inventario de rutas, la nulabilidad y la composición por almacén de S2-471 están en la [auditoría de contratos REST](s2-471-rest-contracts.md).
