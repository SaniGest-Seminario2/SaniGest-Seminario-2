# S2-472 · Inventario REST y plan de limpieza

Base main `454eb69`, con S2-471 y su corrección. Las rutas agrupadas expanden cada verbo y alternativa. Prefijo público `/api/v1` salvo rutas técnicas e internas explícitas. **KEEP** conserva contrato. **UPDATE** reutiliza ruta con evolución compatible. **REMOVE** candidata con migración y prueba de compatibilidad como condición. **INTERNAL** infraestructura, integración o sesión de soporte. **PENDING** objetivo ausente de main. REMOVE registra una decisión condicional de planificación.

| Propietario | Método y ruta | Decisión | Consumidor y ticket |
| --- | --- | --- | --- |
| Identity | POST /auth/{register,login}; GET /auth/me | KEEP | Pantallas de acceso/S2-416, Postman. S2-474 |
| Identity | POST /auth/{refresh,logout} | INTERNAL | Cliente de sesión/S2-416, Postman. S2-474 |
| Identity | POST /internal/v1/member-directory; GET /internal/v1/accounts/{userId}/advisor-eligibility; GET /internal/v1/account-authorization | INTERNAL | Company: directorio, acceso delegado, estado vigente. S2-474 |
| Company | GET/POST /companies; GET/PATCH /companies/{companyId} | KEEP | Selector, onboarding, perfil, smoke. S2-474 |
| Company | GET /companies/{companyId}/users?view=page | UPDATE | Equipo y directorio Identity. S2-474 |
| Company | GET /companies/{companyId}/users sin view | REMOVE | Shape array legado, regresión CompanyProfileIT. Migrar a view=page o /users/all según consumidor. S2-474 |
| Company | POST /companies/{companyId}/invitations; POST /invitations/{token}/accept | UPDATE | Equipo, incorporación Identity, Postman. S2-474 |
| Company | GET /companies/{companyId}/invitations; DELETE /companies/{companyId}/invitations/{invitationId}; PATCH /companies/{companyId}/members/{userId}/{role,status}; DELETE /companies/{companyId}/members/{userId} | KEEP | Administración y ciclo de miembros. S2-474 |
| Company | GET /companies/{companyId}/audit-log | UPDATE | Auditoría vigente, filtros por dominio en S2-480 |
| Company | GET/PUT/DELETE /companies/{companyId}/advisor-access; GET /advisor/companies | KEEP | Concesión y selector delegados, Gateway y CompanyProfileIT. S2-474 |
| Company | GET/PATCH /companies/me; GET /companies/me/users (ambas vistas); GET /companies/me/audit-log; GET/POST /companies/me/invitations; DELETE /companies/me/invitations/{invitationId}; PATCH /companies/me/members/{userId}/{role,status}; DELETE /companies/me/members/{userId}; GET/PUT/DELETE /companies/me/advisor-access | REMOVE | Alias de empresa implícita usados por Postman o cubiertos por CompanyProfileIT según operación. Sustituir por ruta por ID solo tras verificar clientes y despliegues. S2-474, S2-480 para auditoría |
| Company | GET /internal/v2/company-access | INTERNAL | Gateway, Catalog, Sales, Inventory mediante protocolo común. S2-454 |
| Catalog | GET/POST /clients; GET/PATCH /clients/{id}; PATCH /clients/{id}/status | UPDATE | Listado/ficha/formulario y Postman. GET detalle integra Sales con expectedCompanyId. S2-475/S2-457 |
| Catalog | GET/POST /suppliers; GET/PATCH /suppliers/{id}; PATCH /suppliers/{id}/status | UPDATE | Listado/ficha/formulario y Postman. S2-476/S2-346 |
| Catalog | GET/POST /catalog-items; GET/PATCH /catalog-items/{id}; PATCH /catalog-items/{id}/status; GET /catalog-items/summary | UPDATE | Listado, ficha y formulario; detalle con expectedCompanyId consumido por Sales e Inventory. S2-477 |
| Catalog | GET /catalog-items/{id}/history; GET /catalog-items/{units,tax-classifications}; GET /clients/reference-data/{countries,provinces,municipalities,receipt-types}; GET/POST /{clients,suppliers,catalog-items}/categories; PUT /{clients,suppliers,catalog-items}/categories/{id} | KEEP | Historial, referencias, categorías y formularios. S2-473/S2-477 |
| Catalog | GET/PUT /suppliers/{id}/items; GET /catalog-items/{id}/suppliers | UPDATE | Fichas proveedor y artículo. S2-476/S2-477 |
| Inventory | GET/POST /warehouses; GET/PATCH /warehouses/{id}; GET /inventory/{stock,summary,movements}; GET /inventory/stock/{itemId}/{warehouseId}; POST /inventory/adjustments | KEEP | Pantalla stock/kardex; GET stock por lote también integra Catalog; ajuste manual auditado. S2-477/S2-480 |
| Inventory | PUT /inventory/stock/{itemId}/{warehouseId} | UPDATE | Configuración por artículo y almacén, inicialización única. S2-477 |
| Sales | GET/POST /sales; POST /sales/{id}/cancel | UPDATE | Venta, filtros, motor y compensación. S2-461/S2-463/S2-465 |
| Sales | GET /sales/{id}; POST /sales/{saleId}/invoices; GET /receivables/{id} | KEEP | Fichas, emisión desde venta existente, Postman. S2-462/S2-463/S2-471 |
| Servicios y Gateway | GET /v3/api-docs, Swagger donde esté publicado, /actuator/health y probes configuradas | INTERNAL | OpenAPI, herramientas de integración y operación. S2-411/S2-431 |
| Propietarios de la matriz | Rutas y opciones marcadas Pendiente en la matriz | PENDING | Tickets por capacidad en la [matriz v0.9](ui-backend-matrix-v0.9.md), incluyendo S2-473–480 |

## Consumidores comprobados y condiciones de retiro

- Los alias `/companies/me` y el listado de miembros sin vista son compatibles con clientes existentes. Referencias locales comprobadas en [colección Postman](../development/postman/SaniGest.postman_collection.json), [guía de contexto](../development/company-context.md), [CompanyProfileIT](../../services/company-service/src/test/java/com/sanigest/company/integration/CompanyProfileIT.java) y [pruebas del Gateway](../../services/api-gateway/src/test/java/com/sanigest/gateway/integration/ServiceRoutingIT.java). Para asesoría, conservar el endpoint canónico y su validación de elegibilidad aunque el selector no aparezca en el MVP.
- S2-474 debe inventariar llamadas de frontend desplegado y otros clientes externos por método, ruta y forma de respuesta, incluyendo instalaciones con una sola empresa. Este repositorio contiene el backend y sus consumidores de verificación. El uso externo y la ausencia de tráfico no están comprobados. Migrar cada consumidor interno, Postman y OpenAPI, anunciar transición a clientes afectados y comprobar que alias y array legado ya son prescindibles antes de retirarlos. Si la evidencia falta, mantener el contrato de compatibilidad.
- S2-473 reutiliza los enums y catálogos de referencia; cualquier vista de opciones consume la consulta del propietario. S2-475–477 endurecen DTOs y validaciones prospectivas, reutilizan casos de uso y filtros para página, all y selector. Catalog conserva asociaciones, categorías y tasas. Inventory conserva lotes, filtro global S2-471 e historial. Retirar duplicación de lógica solo después de cubrir sus llamadores.
- S2-461/462/463 preservan nombres publicados, snapshots y replay. S2-465 reserva stock para ventas, S2-478 registra recepciones de compras. Ambos reutilizan mecanismos del ledger de Inventory sin enrutar por ajustes manuales. S2-479 comparte el cobro de Sales con CxC y consulta turno Finance. S2-480 extiende auditoría por propietario con S2-412/S2-341. S2-431 valida la transición con contratos, Postman y smoke.
- Consolidar en los tickets propietarios los nombres documentales históricos `/movements` en `/cash-movements` y `/reports/summary` en `/reports/financial-summary`, ambos objetivos futuros. Conservar `number` en factura, `invoiceNumber` en proyecciones y `balance` histórico de CxC. Mantener registros, Flyway aplicadas y mecanismos internos. Las decisiones de propiedad, comunicación y seguridad siguen en ADR-001/002.

## Evidencia de rutas y DTOs

[Contratos S2-471](s2-471-rest-contracts.md) · [Catalog OpenAPI](../../services/catalog-service/src/main/resources/catalog-openapi.json) · [Inventory OpenAPI](../../services/inventory-service/src/main/resources/static/inventory-openapi.json) · [Sales OpenAPI](../../services/sales-service/openapi-invoices.yaml). Se contrastaron los mapeos de controladores de Identity, Company, Catalog, Inventory y Sales. Los cambios previstos se publican en sus respectivos tickets.
