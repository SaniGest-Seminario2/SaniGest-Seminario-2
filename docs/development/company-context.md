# Selección empresarial por petición

Cada pestaña conserva su propia selección. Envía el mismo JWT de Identity junto con
un único `X-Company-Id` UUID en cada petición de negocio. El selector se valida en
Gateway y en el servicio que ejecuta la operación. Company consulta la cuenta
vigente y la membresía del par usuario y empresa en cada autorización.

```http
GET /api/v1/clients?size=20
Authorization: Bearer <access-token>
X-Company-Id: e3fc1fd0-42ea-4a0d-9c14-6cbd44592b42
```

| Ruta y operación | Selector | Permiso | Propietario | Autorización |
| --- | --- | --- | --- | --- |
| `/auth/{login,register,refresh,logout,me}` | Exento | Política de Identity | Identity | Sesión, refresh o JWT según operación |
| `GET/POST /companies` | Exento | REGULAR activo | Company | JWT, estado de cuenta |
| `GET /advisor/companies` | Exento | ADVISOR elegible | Company | JWT, elegibilidad y concesiones vigentes |
| `POST /invitations/{token}/accept` | Empresa del registro de invitación | REGULAR elegible | Company | JWT, email verificado y token de invitación |
| `GET/PATCH /companies/{companyId}` | Path, header opcional coincidente | Membresía para lectura, OWNER/ADMIN para edición, OWNER para RNC | Company | JWT, cuenta y membresía vigente |
| `GET /companies/{companyId}/users` | Path, header opcional coincidente | Membresía activa | Company | JWT, cuenta y membresía vigente |
| `POST /companies/{companyId}/invitations` | Path, header opcional coincidente | OWNER/ADMIN conforme a política de invitación | Company | JWT, membresía y bloqueo transaccional |
| `PATCH /companies/{companyId}/members/{userId}/role`, `DELETE /companies/{companyId}/members/{userId}` | Path, header opcional coincidente | OWNER/ADMIN según rol objetivo y protección del último OWNER | Company | JWT, roles vigentes bajo bloqueo por empresa |
| `GET /companies/{companyId}/audit-log` | Path, header opcional coincidente | OWNER o `AUDIT_LOG_READ` | Company | JWT, membresía y permiso empresarial |
| `GET/PUT/DELETE /companies/{companyId}/advisor-access` | Path, header opcional coincidente | OWNER | Company | JWT, membresía y elegibilidad del asesor |
| `/companies/me` y subrutas equivalentes | Header opcional, compatibilidad singular | Igual que la operación por path | Company | JWT, cuenta y membresía en la empresa seleccionada |
| `/clients`, `/suppliers`, `/catalog-items` y subrutas | Header obligatorio | `CATALOG_READ` o `CATALOG_WRITE` | Catalog | JWT, consulta v2 a Company, filtro por tenant |
| `GET /sales`, `GET /sales/{id}`, `GET /receivables/{id}` | Header obligatorio | `SALES_READ` | Sales | JWT, consulta v2 a Company, filtro por tenant |
| `POST /sales`, `POST /sales/{id}/invoices` | Header obligatorio | `SALES_WRITE` | Sales | JWT, consulta v2, snapshots e idempotencia por tenant |
| `POST /sales/{id}/cancel` | Header obligatorio | `SALES_CANCEL` (OWNER/ADMIN) | Sales | JWT, consulta v2 y política de cancelación |
| Health y endpoints técnicos autorizados | Exentos | Política técnica correspondiente | Cada servicio | Credenciales según el endpoint |

Los paths públicos de la tabla se sirven bajo `/api/v1`. Para `/companies/me` sin
selector, una única empresa activa conserva la compatibilidad. Con varias se
devuelve 409 `COMPANY_SELECTION_REQUIRED`. Las respuestas de ausencia de empresa
siguen la semántica de cada operación.

El selector faltante, inválido o discordante se informa con 400 y los códigos
`COMPANY_CONTEXT_REQUIRED`, `INVALID_COMPANY_CONTEXT` o `COMPANY_CONTEXT_MISMATCH`.
Aplican 401 para JWT inválido, 403 para autorización denegada, 404 para un recurso
invisible dentro del tenant, 503 para autorización indisponible y 504 cuando
vence el timeout del Gateway.

Las concesiones ADVISOR tienen su propia modalidad y validación de elegibilidad.
Los comandos públicos de Catalog y Sales que previamente exigían membresía
regular conservan ese requisito. En Sales → Catalog el servicio propaga el JWT,
la trazabilidad y la empresa que autorizó en `X-Company-Id` y `expectedCompanyId`.
Catalog vuelve a verificar `CATALOG_READ` y pertenencia. Sales rechaza respuestas
con un `X-Catalog-Company-Id` diferente o ambiguo. Tener `SALES_WRITE` por sí solo
no habilita la lectura en Catalog y la venta se rechaza si falla esa consulta.

## Activación local y despliegue coordinado

1. Mantener `COMPANY_MULTIPLE_MEMBERSHIPS_ENABLED=false`. Generar credenciales
   dedicadas por consumidor y configurar la confianza en Company.
2. Coordinar el corte de Company, Catalog y Sales al
   [protocolo v2](../../libraries/common-company-access/README.md) junto con el
   Gateway y los clientes que envían el selector. Los pares v1/v2 incompatibles
   fallan cerrado. Drenar el tráfico durante el cambio o enrutar cada cohorte
   a una pila coherente.
3. Ejecutar verificación del reactor y el smoke integrado en un stack descartable
   con altas múltiples habilitadas. Solo después habilitar la propiedad en el
   entorno que cambia de versión.
4. Para detener nuevas altas ante una regresión, volver a `false` y conservar
   esquema, datos, selector y protocolo v2. Recuperar desplegando una versión
   que mantenga el aislamiento por empresa. Retirar la propiedad cuando todos
   los entornos y clientes operen de forma estable con el contrato multiempresa.

Para un stack local descartable, preparar `.env` con los valores de desarrollo
de `.env.example` y las claves JWT de la [guía de seguridad](../seguridad-s2-317.md).
Generar claves distintas para Catalog, Sales, Inventory y Expense. El usuario 10001 de los
contenedores necesita acceso de lectura a los archivos montados.

```sh
for consumer in catalog sales inventory expense; do
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out ".secrets/${consumer}-company-private.pem"
  openssl pkey -in ".secrets/${consumer}-company-private.pem" -pubout -out ".secrets/${consumer}-company-public.pem"
  setfacl -m "u:10001:r" ".secrets/${consumer}-company-private.pem"
done
COMPANY_MULTIPLE_MEMBERSHIPS_ENABLED=true docker compose -f docker-compose.yml \
  -f infrastructure/qa/compose-multi-company.yml --profile apps up -d --build --wait
python3 infrastructure/qa/smoke-multi-company.py
```

El smoke completo de Gateway (S2-361) se ejecuta con
`sh infrastructure/qa/run-smoke-multi-company.sh`. El runner levanta un stack
descartable con PostgreSQL, Redis y Kafka. Prepara membresías y un turno de caja
como datos de prueba, luego verifica por HTTP desde Gateway los permisos, el
aislamiento, la auditoría y el cobro hasta su proyección en Finance. La invocación
directa de Python omite los escenarios que requieren esos datos iniciales.
El smoke exige que los nombres históricos `/movements` y `/reports/summary`
devuelvan 404. Los alias de empresa mantienen la compatibilidad indicada en el
[inventario REST](../traceability/s2-472-endpoint-lifecycle.md).

Inventory usa el selector en cada petición y verifica `INVENTORY` con alcance `READ` o `WRITE`. Solo admite membresías; Company rechaza las solicitudes delegadas para ese módulo. Catalog verifica `CATALOG_READ` de forma independiente. Consulta la [matriz de Inventory](../../services/inventory-service/README.md).

Expense verifica el selector por petición con Company en `EXPENSE/WRITE`.
Finance deberá aplicar el mismo selector por petición,
verificar módulo y alcance ante Company, comprobar la pertenencia en su
persistencia y transportar el tenant autorizado en eventos, snapshots y claves
idempotentes. Cualquier lectura cacheada deberá estar aislada por empresa y por
los filtros y permisos que determinen el resultado.
