# S2-471 · Contratos REST de negocio

La auditoría parte de `feat/S2-471` sobre `0df3ea2`, que incluye S2-460. La rama todavía usa venta con precios históricos de dos decimales, factura interna desde una venta y obligación automática para CREDIT. Se consultaron [Jira](https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-471), sus tickets relacionados, [Figma](https://www.figma.com/design/yiN50lVICR3jfeiZWGcNmG/SANIGEST), el [contrato v0.8](../SaniGest_T1001_Contrato_Funcional_v0.8.md) y la [matriz UI/API](ui-backend-matrix-v0.8.md).

## Rutas disponibles

Las rutas de negocio son relativas a `/api/v1`. En Company, `{empresa}` representa `{companyId}` y la variante histórica `me`. Los permisos efectivos se detallan en la [guía de contexto empresarial](../development/company-context.md). En la columna Estado, COMPLETE describe el alcance implementado de la ruta.

| Método y ruta | Pantalla o consumidor | Entrada y salida vigente | Estado y evidencia |
| --- | --- | --- | --- |
| POST /auth/register | Registro | Nombre, email, contraseña y tipo de cuenta según el contrato de alta. Devuelve ID de usuario y correo. | COMPLETE · Identity |
| POST /auth/login | Inicio de sesión | Credenciales → token de acceso, expiración y renovación según la sesión. | COMPLETE · Identity |
| POST /auth/refresh | Renovación | Credencial de renovación → token rotado. | COMPLETE · Identity |
| POST /auth/logout | Cierre | Invalida la sesión de renovación. | COMPLETE · Identity |
| GET /auth/me | Perfil | JWT → `id`, nombre, correo y `accountType`. | COMPLETE · Identity |
| GET /companies | Selector | JWT REGULAR → empresas operativas autorizadas, nombres, rol y estado. | COMPLETE · CompanyProfileIT |
| POST /companies | Onboarding | Nombre legal, nombre comercial y datos de contacto → empresa y membresía OWNER. Idempotencia por actor y clave. | COMPLETE · CompanyRegistrationIT |
| GET /companies/{empresa} | Perfil de empresa | Empresa seleccionada → datos empresariales y `email`, `phone` persistidos. | FIX_REQUIRED → corregido · CompanyProfileIT |
| PATCH /companies/{empresa} | Perfil de empresa | Edición parcial, con permiso para RNC → perfil actualizado. | COMPLETE · CompanyProfileIT |
| GET /companies/{empresa}/users | Equipo | Array histórico sin vista. Con `view=page`, directorio por lote, nombre, correo, rol y estado, filtros y conteos. | COMPLETE · CompanyProfileIT |
| POST /companies/{empresa}/invitations | Equipo | Correo, rol, nombre opcional y reemplazo → invitación con nombre y correo normalizados. | FIX_REQUIRED → corregido · CompanyTeamControllerTest |
| GET /companies/{empresa}/invitations | Equipo | `page`, `size` → array histórico de invitaciones pendientes con nombre, correo, rol y vencimiento. | COMPLETE · CompanyProfileIT |
| DELETE /companies/{empresa}/invitations/{invitationId} | Equipo | Revoca una invitación pendiente → 204. | COMPLETE · CompanyProfileIT |
| POST /invitations/{token}/accept | Incorporación | Token de un solo uso y cuenta elegible → membresía. | COMPLETE · CompanyProfileIT |
| PATCH /companies/{empresa}/members/{userId}/role | Equipo | Rol autorizado → membresía. | COMPLETE · CompanyProfileIT |
| PATCH /companies/{empresa}/members/{userId}/status | Equipo | ACTIVE/SUSPENDED → membresía. | COMPLETE · CompanyProfileIT |
| DELETE /companies/{empresa}/members/{userId} | Equipo | Revocación autorizada → 204. | COMPLETE · CompanyProfileIT |
| GET /companies/{empresa}/audit-log | Auditoría | Filtros de actividad y página → actividad empresarial. | COMPLETE · CompanyProfileIT |
| GET /companies/{empresa}/advisor-access | Acceso delegado | OWNER → concesión vigente. | COMPLETE · CompanyProfileIT |
| PUT /companies/{empresa}/advisor-access | Acceso delegado | OWNER, cuenta elegible y permisos → concesión. | COMPLETE · CompanyProfileIT |
| DELETE /companies/{empresa}/advisor-access | Acceso delegado | OWNER → revocación. | COMPLETE · CompanyProfileIT |
| GET /advisor/companies | Selector del asesor | JWT ADVISOR → empresas con concesión vigente y módulos. | COMPLETE · CompanyProfileIT |
| GET /clients | Clientes | Búsqueda, tipo, condición, categoría, estado y página → ID, nombre, tipo, teléfono, estado, condición y categoría. | COMPLETE para datos maestros · CatalogServiceIT |
| POST /clients | Alta de cliente | Tipo y nombre requeridos. País, condición y categoría tienen defaults. → ficha comercial. | FIX_REQUIRED → corregido · CatalogWebPolicyTest, CatalogServiceIT |
| GET /clients/{id} | Ficha | UUID → perfil, identificación y contacto. Sales conserva comprobación `expectedCompanyId`. | COMPLETE · CatalogServiceIT |
| PATCH /clients/{id} | Ficha | PATCH tipado → ficha vigente. | COMPLETE · CatalogServiceIT |
| PATCH /clients/{id}/status | Clientes | Estado → ficha vigente. | COMPLETE · CatalogServiceIT |
| GET, POST /clients/categories · PUT /clients/categories/{id} | Categoría de cliente | Código/etiqueta/estado → categorías de la empresa con ID. | COMPLETE · CatalogServiceIT |
| GET /clients/reference-data/countries, /provinces, /municipalities, /receipt-types | Ficha y dropdowns | Códigos de país/provincia cuando corresponda → códigos y etiquetas. | COMPLETE · CatalogServiceIT |
| GET /suppliers | Proveedores | Búsqueda, suministro, condición, categoría, estado y página → contacto, teléfono y datos de tabla. | COMPLETE para datos maestros · CatalogServiceIT |
| POST /suppliers | Alta | Nombre y contacto requeridos. CASH al omitir condiciones → ficha comercial. | FIX_REQUIRED → corregido · CatalogWebPolicyTest, CatalogServiceIT |
| GET /suppliers/{id} | Ficha | UUID → perfil e identificación. | COMPLETE · CatalogServiceIT |
| PATCH /suppliers/{id} | Edición | PATCH tipado. Condición explícita null inválida → ficha vigente. | FIX_REQUIRED → corregido · SupplierCommercialDtoTest |
| PATCH /suppliers/{id}/status | Proveedores | Estado → ficha vigente. | COMPLETE · CatalogServiceIT |
| GET, POST /suppliers/categories · PUT /suppliers/categories/{id} | Categoría de proveedor | Código, nombre y estado → categorías de empresa. | COMPLETE · CatalogServiceIT |
| GET /suppliers/{id}/items · GET /catalog-items/{id}/suppliers | Relaciones | Búsqueda, estado y página → artículo o proveedor, costo ofertado, referencia y designación principal. | COMPLETE · CatalogServiceIT |
| PUT /suppliers/{id}/items | Relaciones | Sustitución transaccional de ofertas → 204. | COMPLETE · CatalogServiceIT |
| POST /catalog-items | Alta | Nombre, tipo, precio, unidad y clasificación fiscal → artículo, divisibilidad y proyección comercial. | FIX_REQUIRED → corregido · CatalogWebPolicyTest, CatalogServiceIT |
| GET /catalog-items | Tabla | Búsqueda por artículo, SKU, código de barras o categoría, filtros comerciales y página → datos comerciales y proveedor principal legible. El filtro combinado usa `warehouseId` y `availability` antes de paginar. | FIX_REQUIRED → corregido · CatalogWebPolicyTest, CatalogServiceIT |
| GET /catalog-items/{id} | Ficha e integraciones | UUID, selector, `expectedCompanyId` en integraciones → detalle y proveedor principal legible. | FIX_REQUIRED → corregido · CatalogWebPolicyTest, CatalogServiceIT |
| PATCH /catalog-items/{id} | Edición | PATCH tipado → detalle. Unidad y clasificación explícitamente nulas se rechazan. | FIX_REQUIRED → corregido · CatalogWebPolicyTest, CatalogServiceIT |
| PATCH /catalog-items/{id}/status | Tabla | Flag `active` → detalle comercial. | FIX_REQUIRED → corregido · CatalogWebPolicyTest |
| GET, POST /catalog-items/categories · PUT /catalog-items/categories/{id} | Dropdown de artículos | Código, nombre y estado → categorías empresariales. | COMPLETE · CatalogServiceIT |
| GET /catalog-items/units · GET /catalog-items/tax-classifications | Formulario | Referencias de unidad, divisibilidad, clasificación, tasa y vigencia. | COMPLETE · CatalogServiceIT |
| GET /catalog-items/summary | Productos y servicios | Conteos comerciales empresariales. | COMPLETE · CatalogServiceIT |
| GET /catalog-items/{id}/history | Historial | Página → cambios auditados y `totalPages`. | FIX_REQUIRED → corregido · CatalogWebPolicyTest |
| GET, POST /warehouses · GET, PATCH /warehouses/{id} | Almacenes | Página o metadatos → código, nombre, ubicación, estado y revisión. | COMPLETE · InventoryWebTest |
| GET /inventory/stock | Existencias | Filtros de almacén, disponibilidad, texto e IDs. Lote acotado de un almacén → posiciones y nombre del almacén. | FIX_REQUIRED → corregido · StockLedgerIT |
| GET /inventory/stock/{itemId}/{warehouseId} | Existencia | Posición o referencia verificada. `NOT_APPLICABLE` para SERVICE y `NOT_TRACKED` para producto sin configuración. | FIX_REQUIRED → corregido · InventoryWebTest |
| PUT /inventory/stock/{itemId}/{warehouseId} | Configuración | Tracking, umbrales, ubicación y existencia inicial permitida una vez → posición con `warehouseName`. | COMPLETE · StockLedgerIT |
| GET /inventory/summary | Inventario | Almacén opcional → conteos de posiciones. | COMPLETE · StockLedgerIT |
| GET /inventory/movements | Kardex | Filtros y página → movimiento inmutable, unidad, motivo, actor y origen. | COMPLETE · StockLedgerIT |
| POST /inventory/adjustments | Ajustes | Artículo, almacén, dirección, cantidad y motivo → movimiento. Clave idempotente en despliegue normal. | COMPLETE · StockLedgerIT |
| GET /sales | Tabla | Nombre de cliente, estado documental, fechas y página → snapshot, factura vigente y estado de obligación. | FIX_REQUIRED → corregido · SalePageResponseTest, SalesServiceIT |
| POST /sales | Venta | Cliente, líneas, modo, vencimiento CREDIT y clave → venta registrada. | COMPLETE para S2-460 · SalesServiceIT |
| GET /sales/{id} | Ficha | UUID → venta y líneas congeladas, referencias y finanzas de la obligación, cuando exista. | FIX_REQUIRED → corregido · SalesServiceIT |
| POST /sales/{id}/cancel | Venta | Motivo autorizado → snapshot de cancelación. | COMPLETE · SalesServiceIT |
| POST /sales/{saleId}/invoices | Emitir factura interna | Venta existente, clave y cuerpo vacío → documento interno con `number`, snapshots y totales. | COMPLETE para emisión vigente · SalesServiceIT |
| GET /receivables/{id} | Ficha CxC | UUID → cliente, saldo, vencimiento, `saleCode`, `invoiceId`, `invoiceNumber`. | FIX_REQUIRED → corregido · SalesServiceIT |

Gateway enruta los prefijos públicos anteriores y verifica JWT y selector según la ruta. `/actuator/health`, sus probes, `/v3/api-docs` y Swagger donde se publiquen son contratos técnicos. `POST /internal/v1/member-directory`, `GET /internal/v1/accounts/{userId}/advisor-eligibility`, `GET /internal/v1/account-authorization` y `GET /internal/v2/company-access` son INTERNAL. Las variantes de detalle de Catalog con `expectedCompanyId` siguen disponibles para Sales e Inventory. Los módulos Expense y Finance carecen de ejecutable en esta base.

## Nulabilidad y compatibilidad

| Campo y propietario | Escritura | Lectura |
| --- | --- | --- |
| Catalog · `name`, `clientType` | REQUIRED en altas | REQUIRED para altas nuevas. Tipo histórico LEGACY_UNKNOWN. |
| Catalog · `countryCode`, `paymentTerms`, `categoryId` de cliente | OPTIONAL por omisión con defaults DO, CASH y REGULAR. Null explícito inválido. | REQUIRED en perfiles completos. LEGACY_UNKNOWN para filas sin perfil. |
| Catalog · contacto y condición de proveedor | Contacto REQUIRED, condición por defecto CASH. | Condición LEGACY_UNKNOWN en proveedores sin perfil anterior. |
| Catalog · `supplyType`, `taxId`, `phone`, `address`, `categoryId` de proveedor | OPTIONAL según v0.8. `creditDays` solo para CREDIT/BOTH. | OPTIONAL. |
| Catalog · `unitCode`, `taxClassificationCode` | REQUIRED en alta. En PATCH, omisión conserva y null explícito devuelve 400. | LEGACY_UNKNOWN en filas anteriores. |
| Catalog · `active`, `currency`, `availableForSale`, `availableForPurchase` | Estado y moneda DERIVED en alta (activo, DOP). Flags omitidos se inicializan a true y false, respectivamente. | Flag de compra histórico nullable. Estado y flags representan conceptos independientes. |
| Catalog · `estimatedCost`, `grossProfit`, `grossMargin`, `finalPrice`, `primarySupplierName` | Costo OPTIONAL. Los demás son DERIVED. | Cálculos nulos si falta un operando o el margen tiene denominador cero. Proveedor principal opcional. |
| Company · `email`, `phone`, `name` de invitación | Contacto según onboarding. Nombre invitado OPTIONAL. | Datos persistidos. Nombre de invitación nullable. Nombre y correo del directorio pueden faltar si no hay entrada para el usuario. |
| Sales · `dueDate` | REQUIRED_IF CREDIT. | Disponible en la lectura desde la obligación. |
| Sales · `invoiceNumber`, `paymentStatus`, `outstandingBalance`, `collectedAmount` | DERIVED. | Null si falta el documento u obligación correspondiente. `OPEN`, `PARTIALLY_PAID`, `PAID` y `CANCELLED` son estados de obligación independientes de `REGISTERED` y `CANCELLED` de venta. |
| Inventory · cantidades físicas | DERIVED en lecturas. | NOT_APPLICABLE para servicios. NOT_TRACKED para productos sin seguimiento. Una posición ausente conserva cantidades null. |

Los clientes que escribían payloads históricos deben enviar `clientType`, y las altas de artículos deben enviar `unitCode` y `taxClassificationCode`. Los campos controlados por el servidor se rechazan en los payloads de creación. Los ejemplos Postman del workspace ya usan los campos requeridos. Las respuestas aditivas conservan nombres publicados como `companyId` en el selector, `number` en la factura interna y `balance` en cuentas por cobrar. Para código nuevo, el saldo se lee de `outstandingBalance`. Los resultados idempotentes persistidos y snapshots siguen con su formato original.

Para la tabla general de productos, consultar una página de Catalog y consultar Inventory una sola vez con `warehouseId`, `catalogItemIds` (hasta 100) y `size` suficiente para la página. Asociar por `catalogItemId`. El lote contiene posiciones configuradas, incluidas las deshabilitadas, y sus filtros se aplican en PostgreSQL antes del cálculo de la página. Con una respuesta satisfactoria, la ausencia de una posición para un producto de la página indica NOT_TRACKED en ese almacén; para SERVICE la UI puede mostrar «No aplica» con el tipo de Catalog. Un fallo HTTP de Inventory deja el dato indisponible. Los permisos de Catalog e Inventory se verifican en cada consulta.

Para filtrar el catálogo completo, usa `GET /catalog-items?warehouseId={uuid}&availability=OUT&size=20` combinando los filtros comerciales habituales. Catalog recorre por nombre e ID con lotes de hasta 100 y solicita las posiciones a Inventory con el JWT original y el tenant autorizado. Cada lote lee la disponibilidad vigente. Catalog mantiene una sola página de salida y devuelve el total después del filtro. Se devuelve 503 si la composición no termina dentro de su presupuesto o Inventory falla. El almacén ajeno o ausente produce 404.

En ventas y CxC, `outstandingBalance` representa saldo cobrable y es `"0.00"` cuando la obligación está cancelada. `balance` conserva el histórico y `collectedAmount` deriva del importe original menos el balance histórico.

## Dependencias y pendientes

| Clasificación | Capacidad | Jira |
| --- | --- | --- |
| BLOCKED_BY_DEPENDENCY | Saldo por cliente, métricas, crédito disponible y estado de cuenta. | S2-457 |
| BLOCKED_BY_DEPENDENCY | Precisión comercial de cuatro decimales en venta, descuentos e ITBIS congelados por línea. | S2-461 |
| NOT_IMPLEMENTED | Factura desde formulario, listado y ficha. | S2-462, S2-336 |
| NOT_IMPLEMENTED | Indicadores de venta, cobros, cartera y reversos. | S2-463, S2-338–341 |
| NOT_IMPLEMENTED | Coordinación de recepciones y reservas comerciales. | S2-465 |
| NOT_IMPLEMENTED | Compras y gastos (`/expenses`) y cuentas por pagar (`/payables`), de Expense. | S2-342–348 |
| NOT_IMPLEMENTED | Caja y cobros (`/cash-sessions` en Finance y cobros en Sales). | S2-464 |
| NOT_IMPLEMENTED | Ingresos y egresos (`/movements`), reportes (`/reports`) y CSV, de Finance. | S2-349–355, S2-467–469 |
| NOT_IMPLEMENTED | Dashboard (`/dashboard`) con datos de los servicios propietarios. | S2-353, S2-468 |

El [OpenAPI de Catalog](../../services/catalog-service/src/main/resources/catalog-openapi.json), el [OpenAPI de Inventory](../../services/inventory-service/src/main/resources/static/inventory-openapi.json) y el [OpenAPI de Sales](../../services/sales-service/openapi-invoices.yaml) detallan los formatos. S2-411 conserva la estandarización global de OpenAPI y S2-431 la aceptación de la integración frontend.

## Ejemplos para frontend

Alta mínima de cliente:

```json
{"name":"Ana Pérez","clientType":"NATURAL"}
```

Lectura de stock de un servicio por almacén, campos relevantes:

```json
{"catalogItemId":"08402167-f156-4cc9-bcd6-9b1f8a139287","warehouseId":"d0dc14ff-ac3a-48a2-a54d-dea342642833","warehouseName":"Principal","configured":false,"trackingEnabled":false,"onHand":null,"reserved":null,"available":null,"availability":"NOT_APPLICABLE"}
```

Detalle de una venta con obligación y factura interna, campos relevantes:

```json
{"code":"V-2026-001","invoiceNumber":"FAC-2026-001","mode":"CREDIT","status":"REGISTERED","paymentStatus":"OPEN","dueDate":"2026-12-23","total":"3500.00","collectedAmount":"0.00","outstandingBalance":"3500.00","currency":"DOP"}
```

La migración V17 preserva los registros existentes y protege contra el borrado de unidad, clasificación, flag de compras y condición de pago previamente conocidos. Las altas HTTP y los casos de uso aplican la obligatoriedad prospectiva, con las restricciones de referencias existentes. V18 aplica estas reglas también a las inserciones directas. Las filas históricas conservan sus datos y admiten las actualizaciones válidas bajo V17.
