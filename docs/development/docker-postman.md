# Ejecutar SaniGest con Docker y probarlo en Postman

## 1. Levantar el backend

Ejecuta los comandos desde la raíz del repositorio. Necesitas Docker con Compose v2, OpenSSL y Postman. Docker compila los servicios durante el build.

```sh
test -f .env || cp .env.example .env
```

En `.env`, establece `SANIGEST_NETWORK_INTERNAL=false` para acceder a los puertos desde Postman. Conserva las rutas de claves de `.env.example` y añade `COMPANY_MULTIPLE_MEMBERSHIPS_ENABLED=true` si quieres crear varias empresas con el mismo usuario. Si ya tienes claves locales, conserva cada par completo.

```sh
mkdir -p .secrets
chmod 700 .secrets
for pair in jwt catalog-company inventory-company sales-company expense-company member-directory advisor-eligibility; do
  if [ ! -f ".secrets/$pair-private.pem" ]; then
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out ".secrets/$pair-private.pem"
  fi
  if [ ! -f ".secrets/$pair-public.pem" ]; then
    openssl pkey -in ".secrets/$pair-private.pem" -pubout -out ".secrets/$pair-public.pem"
  fi
  chmod 644 ".secrets/$pair-private.pem" ".secrets/$pair-public.pem"
done
```

El directorio está restringido al usuario del host. Docker monta los archivos individuales en modo de solo lectura, y el UID 10001 de las imágenes puede leer las claves montadas. Usa estas claves exclusivamente en desarrollo local.

```sh
sanigest() {
  docker compose -f docker-compose.yml \
    -f infrastructure/qa/compose-multi-company.yml \
    -f docs/development/postman/compose-identity-integrations.yml \
    --profile apps "$@"
}

sanigest config --quiet
sanigest up -d --build --wait --wait-timeout 180
sanigest ps
curl -fsS http://127.0.0.1:8085/actuator/health/readiness
```

El Compose base inicia PostgreSQL, Valkey, Kafka y seis servicios de negocio. El archivo de QA añade el Gateway, habilita el selector de empresa para Catalog y Sales, y configura las llamadas de autorización de Sales y Expense a Company. El tercer archivo configura el directorio de miembros y la consulta de elegibilidad de asesores entre Company e Identity.

| Componente | Dirección local | Uso |
| --- | --- | --- |
| API Gateway | `http://127.0.0.1:8085` | Entrada de las peticiones de negocio |
| Company | `http://127.0.0.1:8080` | Empresas, invitaciones, miembros y asesores |
| Identity | `http://127.0.0.1:8081` | Registro, login, perfil y sesión |
| Catalog | `http://127.0.0.1:8083` | Clientes, proveedores, artículos y sus referencias |
| Sales | `http://127.0.0.1:8084` | Ventas, cuentas por cobrar y facturas internas |
| Inventory | `http://127.0.0.1:8086` | Almacenes, existencias y movimientos |
| Expense | `http://127.0.0.1:8087` | Registro de compras y gastos |
| PostgreSQL, Valkey y Kafka | `5432`, `6379`, `9092` | Dependencias de infraestructura |

Finance cuenta con un esquema de desarrollo para su futura aplicación HTTP.

Si ya tienes un volumen de PostgreSQL anterior a Inventory, añade sus variables de `.env.example` a `.env` y ejecuta el [bootstrap dirigido de Inventory](../../services/inventory-service/README.md) antes de iniciar las aplicaciones. PostgreSQL ejecuta los scripts de inicialización automáticamente al crear un volumen nuevo.

```sh
sanigest logs -f --tail=100
sanigest logs --tail=100 company-service
sanigest down
```

`down` conserva los datos en los volúmenes. Si cambias un puerto publicado, actualiza la variable de URL correspondiente en Postman. En otra terminal, vuelve a declarar la función `sanigest` para usar los mismos archivos.

## 2. Importar y ejecutar la colección

Importa la [colección SaniGest](postman/SaniGest.postman_collection.json) y el [entorno SaniGest local](postman/SaniGest.local.postman_environment.json). Selecciona ese entorno en Postman antes de enviar las peticiones.

| Carpeta | Orden y resultado |
| --- | --- |
| **00 Salud y documentación** | Comprueba salud, liveness y readiness de los seis componentes HTTP. Incluye `info` donde está expuesto y los contratos de Catalog e Inventory. |
| **01 Identidad y sesión** | Ejecuta **register → login → me → refresh**. El registro genera un correo de prueba. Login y refresh guardan el access token y el refresh token en el entorno. |
| **02 Empresa** | Crea la empresa para guardar `companyId`. Consulta y edita con UUID o con `/companies/me`, y comprueba el historial de auditoría. El OpenAPI de Company requiere el JWT de usuario. |
| **03 Catálogo** | Crea primero las categorías y luego el cliente, el proveedor y el producto. Los scripts guardan sus IDs. Prueba referencias, filtros básicos, ediciones, estados y la relación proveedor–artículo. |
| **04 Inventario** | Crea el almacén, consulta la posición, configura 20 unidades iniciales y registra una salida de 3. Vuelve a consultar la posición y comprueba que `onHand` queda en 17. Consulta el resumen y los movimientos. |
| **05 Ventas y facturas** | Registra una venta al contado, consulta el detalle y emite una factura interna. Luego registra una venta a crédito, consulta la cuenta por cobrar con el ID de esa venta, cancélala y vuelve a consultar la cuenta. |
| **06 Equipo e invitaciones** | Lista miembros, crea y revoca invitaciones, y usa un miembro existente para probar cambios de rol, suspensión y revocación. |
| **07 Asesores** | Consulta, concede o retira permisos de asesor y lista sus empresas con la sesión del asesor. |
| **08 Caso negativo y rutas internas** | Prueba `401` sin sesión y `400` sin selector de empresa. Incluye peticiones técnicas y la ruta de diagnóstico del Gateway. |
| **09 Cerrar sesión** | Envía el refresh token vigente para revocar la sesión. Ejecuta esta carpeta al final. |

Puedes usar **Send** en ese orden o ejecutar las carpetas **00 a 05** en el Runner. Cada solicitud comprueba el estado HTTP esperado. Ejecuta registro y login de nuevo para empezar con datos aislados. Si repites una prueba con datos existentes, comprueba qué IDs están seleccionados antes de enviar escrituras.

La edición de categorías de proveedor usa directamente `catalogUrl`. El Gateway actual publica el listado y el alta de esas categorías.

Las peticiones de negocio heredan `Authorization: Bearer {{accessToken}}`. Catalog, Inventory y Sales llevan también `X-Company-Id: {{companyId}}`. Para probar un reintento idempotente, asigna un valor a `idempotencyOverride`, envía dos veces la misma petición con el mismo cuerpo y borra el valor al terminar. Por defecto, cada envío genera una clave nueva. La factura interna se emite con una petición sin cuerpo.

`/companies/me` funciona con una sola membresía activa o con un selector `X-Company-Id` explícito. Si el usuario tiene varias empresas y recibe `409`, añade el encabezado o usa la variante con UUID.

## 3. Preparar casos con datos adicionales

- **Equipo.** Para cambiar un miembro, copia su `userId` desde la lista paginada a `memberUserId`. Usa un miembro de prueba distinto del último propietario. Los endpoints de invitaciones permiten crear, listar y revocar invitaciones desde este stack. La aceptación completa requiere el token original y una cuenta con correo verificado. El registro local devuelve `emailVerified: false` y todavía está pendiente la entrega del token. El flujo exitoso de aceptación se cubre con fixtures de Company. [Contrato de equipo e invitaciones](../../services/company-service/README.md).
- **Asesores.** Usa un asesor activo de prueba. Pon el ID en `advisorId` y su JWT de login en `advisorToken`. Conserva el JWT del propietario en `accessToken`. Para una concesión inicial usa `advisorRevision=0`. En ediciones posteriores, usa la revisión retornada por Company. Después de revocar, consulta la revisión vigente antes de volver a conceder acceso. El registro público crea cuentas regulares. Puedes preparar un asesor en la base local desechable con el ejemplo de abajo.
- **Rutas internas.** Envía las peticiones directamente a Identity o Company. `account-authorization` usa el JWT del usuario y, cuando la cuenta está activa, devuelve `204` con `X-Account-Type`. Las demás peticiones requieren los JWT técnicos indicados en sus descripciones. Los servicios los generan al ejecutar los flujos públicos correspondientes. Para una prueba directa con credenciales técnicas, usa los contratos de los emisores y configura las variables de la carpeta 08. [Contrato interno de Identity](../../services/identity-service/README.md) · [Contrato de acceso empresarial](company-context.md).
- **Gateway ping.** Requiere iniciar el Gateway con el perfil `local` y configurar `GATEWAY_TEST_BACKEND_URL` hacia un servicio de diagnóstico que responda a `GET /ping`. La configuración de arriba deja esta ruta desactivada. Usa readiness para el chequeo normal. [Configuración del Gateway](../../services/api-gateway/README.md).

Para preparar un asesor exclusivamente en el entorno local, registra una segunda cuenta con `POST /api/v1/auth/register` y cambia su correo de prueba en el ejemplo. Usa **Send** después de editar el cuerpo y desactiva el script de pre-request en una copia de esa petición para evitar que sustituya el correo. Con los nombres de base y rol de `.env.example`:

```sh
sanigest exec -T postgres psql -U sanigest_identity -d sanigest_identity -v ON_ERROR_STOP=1 \
  -c "UPDATE public.users SET account_type='ADVISOR', updated_at=now() WHERE email='asesor-local@example.test' AND status='ACTIVE' RETURNING id;"
```

Copia el UUID de `RETURNING` a `advisorId`. Para obtener su token, duplica el login y coloca el correo del asesor en el cuerpo. Cambia el script que guarda el token para que escriba en `advisorToken`, así se conserva la sesión del propietario en el entorno.

## 4. Verificación automática

El smoke del repositorio levanta su propio stack desechable en puertos alternativos y verifica registro, login, creación de empresas, aislamiento de clientes y ventas, almacenes, existencias, ajustes y registro de compras.

```sh
sh infrastructure/qa/run-smoke-multi-company.sh
```

Consulta los contratos para validar filtros y campos adicionales. [Catalog](../../services/catalog-service/src/main/resources/catalog-openapi.json) · [Inventory](../../services/inventory-service/src/main/resources/static/inventory-openapi.json) · [Sales](../../services/sales-service/openapi-invoices.yaml) · [Expense](../../services/expense-service/openapi-expenses.yaml).
