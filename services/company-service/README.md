# Company Service

Gestiona empresas, perfiles, membresías, invitaciones y auditoría. Persiste
sus datos en `sanigest_company` y consulta Identity por HTTP para resolver el
tipo de cuenta del usuario autenticado.

- `api/controller` expone los endpoints. `api/dto` define JSON y validación.
  `api/error` convierte excepciones en respuestas HTTP.
- `application/usecase` agrupa perfil, equipo y auditoría. `application/dto`,
  `application/exception` y `application/port/out` contienen entradas,
  resultados, errores y contratos externos de los casos de uso.
- `domain/model` contiene entidades y reglas. Los enums de dominio viven en `domain/enums`.
- `infrastructure/persistence/jdbc` implementa los puertos con Spring
  JDBC. Company usa PostgreSQL y Flyway. Los adaptadores conservan los
  bloqueos transaccionales de PostgreSQL y la lectura de valores generados
  mediante `RETURNING`.
- `infrastructure/identity` contiene el cliente HTTP de Identity.
  `infrastructure/security` configura la validación JWT.

Los puertos de salida se agrupan por persistencia, seguridad o integraciones según el servicio.

Las pruebas unitarias reflejan los paquetes del código. El paquete
`integration` contiene las pruebas HTTP, de persistencia y de migraciones.

El caso de uso de registro conserva una única transacción para empresa,
membresía OWNER y clave de idempotencia. Las migraciones de Flyway pertenecen a
este servicio y viven en `src/main/resources/db`.

Desde la raíz:

```bash
mvn -B -pl services/company-service -am verify
```

Consulta el [arranque local](../../README.md) y el
[contrato objetivo v0.8](../../docs/SaniGest_T1001_Contrato_Funcional_v0.8.md).

## Alta y listado de empresas (S2-453)

En ambas operaciones se requiere un JWT de usuario y una cuenta `REGULAR`
activa confirmada por Identity. El sujeto del JWT identifica al usuario.
Una cuenta nueva puede consultar sus empresas antes de crear la primera.

```http
POST /api/v1/companies
Authorization: Bearer <access-token>
Idempotency-Key: registro-001
Content-Type: application/json

{"companyName":"Distribuidora La Económica SRL","businessName":"Distribuidora La Económica","rnc":"123456789","email":"administracion@example.com","phone":"8095550100","location":"Santo Domingo Este"}
```

El alta devuelve **201** con `companyId`, los datos de la empresa, moneda
`DOP` y estado `ACTIVE`. Company asigna una membresía `OWNER` activa al
usuario autenticado. Los campos históricos `ownerId` y `userId` siguen
participando en el hash de idempotencia. La identidad y los valores asignados
por el servidor se determinan en Company.

```http
GET /api/v1/companies
Authorization: Bearer <access-token>
```

Respuesta **200**, ordenada por razón social y UUID, con `[]` si el usuario
carece de empresas operativas. La consulta incluye solo membresías y empresas
activas y obtiene el rol de cada membresía. Ambas rutas funcionan sin selector.

```json
[
  {
    "companyId": "e3fc1fd0-42ea-4a0d-9c14-6cbd44592b42",
    "companyName": "Distribuidora La Económica SRL",
    "businessName": "Distribuidora La Económica",
    "role": "OWNER",
    "membershipStatus": "ACTIVE",
    "status": "ACTIVE"
  }
]
```

La clave de idempotencia tiene de 1 a 128 caracteres, sin caracteres de control
y con al menos un carácter distinto de espacio en blanco. Su alcance es el
usuario. Un reintento idéntico devuelve la empresa original si la membresía
y la empresa siguen activas. Un cuerpo diferente con la misma clave o un RNC
duplicado devuelve **409**. Si Identity no puede confirmar la autorización,
la operación devuelve **503**.

PostgreSQL serializa operaciones de alta por usuario con un bloqueo de
transacción. Así, cada reintento lee un registro confirmado y la regla de
compatibilidad cubre altas con distintas claves y aceptaciones de invitación
concurrentes. Empresa, membresía y clave se confirman juntas. El reintento
bloquea la membresía de la empresa original por el par empresa–usuario.
Flyway V10 elimina la restricción global de usuario. Conserva la unicidad
empresa–usuario, el índice de usuario y todos los registros existentes.

### Activación coordinada

`COMPANY_MULTIPLE_MEMBERSHIPS_ENABLED` vale `false` por defecto. En ese modo
el alta adicional devuelve **409** y el listado está disponible. Para aplicar
V10, detén las escrituras de alta y aceptación en réplicas anteriores a
S2-453 y restablécelas cuando todas las réplicas usen el bloqueo compartido.
Tras desplegar la selección explícita y la validación por empresa de S2-454 en
Gateway, Company y los consumidores de autorización, configura esta variable
en `true` en todas las réplicas de Company. Para comprobar el flujo en un
entorno desechable con Identity y Company, activa la variable y ejecuta:

```bash
mvn -B -pl services/company-service -am -Psmoke \
  -Dsmoke.identity.url=http://localhost:8081 \
  -Dsmoke.company.url=http://localhost:8082 \
  -Dsmoke.company.multi=true verify
```

Los consumidores de `/companies/me` y sus subrutas pueden seguir operando
si hay una sola empresa activa autorizada. Si hay varias, reciben **409**
con `COMPANY_SELECTION_REQUIRED`. Envía `X-Company-Id` o usa las rutas explícitas
`/companies/{companyId}`. Company verifica el par actor y empresa en cada consulta
interna v2. S2-455 amplía las invitaciones y el listado de miembros. La activación
y el rollback están descritos en la [guía de contexto empresarial](../../docs/development/company-context.md).

## Architecture rules

[CompanyArchitectureTest](src/test/java/com/sanigest/company/architecture/CompanyArchitectureTest.java)
usa ArchUnit sobre las clases compiladas de producción.

- API usa los casos de uso de Application y puede mapear modelos de Domain.
  Sus controladores y DTO quedan separados de la persistencia, los puertos de
  salida y los clientes HTTP remotos. Se permite el modelo de lectura
  `MembershipRepository.ActiveAccess` que devuelve `ResolveCompanyAccess`
  para mapear la respuesta de autorización.
- Application usa Domain y sus propios contratos. Puede usar componentes y
  transacciones de Spring, con el transporte y las tecnologías de persistencia
  fuera de los casos de uso y de sus puertos.
- Domain usa sus propios tipos y los tipos de Java ajenos a transporte y
  persistencia. Infrastructure implementa los puertos de Application, usa
  Domain y mantiene los repositorios en `infrastructure.persistence.jdbc`,
  separados de los controladores y DTO HTTP.
- Las capas y los demás paquetes de primer nivel quedan libres de ciclos. Se
  prohíben las dependencias de código con otros servicios ejecutables. Las
  bibliotecas compartidas `common-security` y `common-company-access` se pueden
  usar donde lo permitan las reglas de capa.

Las reglas se ejecutan con `mvn -B -pl services/company-service -am verify` y
en el job **Unit tests** de Company. Para ejecutarlas de forma aislada desde la raíz:

```bash
mvn -B -pl services/company-service -am -Dtest=CompanyArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Un fallo muestra la regla y las clases o miembros implicados en el reporte
Surefire, y hace fallar Maven y el job de CI.

Agrega reglas como métodos JUnit en `CompanyArchitectureTest`. Selecciona
clases de producción existentes y usa `.check(PRODUCTION)`. Por ejemplo, si
se decide aislar también la implementación de auditoría de los controladores,
la regla sería:

```java
@Test
void controllersDoNotUseAuditInternals() {
  noClasses()
      .that().resideInAPackage(COMPANY + ".api.controller..")
      .should().dependOnClassesThat()
      .haveFullyQualifiedName(COMPANY + ".application.usecase.audit.CompanyAuditRecorder")
      .check(PRODUCTION);
}
```

Comprueba que una dependencia prohibida provoque el fallo esperado. Si el
wiring necesita una excepción, documenta las clases implicadas, su
responsabilidad y el motivo junto a la regla, y limita la excepción a esas
dependencias. Para los límites de datos, la comunicación entre servicios y
cambios a esas decisiones, consulta [ADR-001](../../docs/adr/ADR-001-propiedad-datos-comunicacion-servicios.md).

## Acceso delegado a asesores (S2-313)

Las tres operaciones en `/api/v1/companies/me/advisor-access` requieren JWT de
usuario, cuenta `REGULAR` activa y membresía `OWNER` activa en una empresa activa.
Company obtiene la empresa de la membresía y verifica la cuenta del actor con
Identity. El destinatario se identifica por `advisorId` y conserva su rol global.

- **GET 200** devuelve una lista de `{advisorId, revision, permissions}` de la
  empresa del actor. Una lista de permisos vacía para un asesor indica acceso
  revocado y conserva la revisión necesaria para una concesión nueva.
- **PUT 200** reemplaza el conjunto completo de permisos del asesor. Ejemplo:
  `{"advisorId":"04bf866e-2508-4f80-a76e-b1fe24003ea0","revision":0,"permissions":[{"module":"CATALOG"}]}`.
  Omita `revision` o envíe cero para la primera concesión. Para cambiar el
  conjunto o volver a conceder acceso, envíe la revisión observada en GET.
  Repetir el mismo conjunto vigente conserva la revisión y la auditoría.
  Una escritura que cambiaría el estado con revisión obsoleta devuelve **409**.
- **DELETE 204** recibe `advisorId` en query y revoca todos sus permisos para
  esa empresa. Es idempotente. Si llega antes del primer PUT, conserva una
  revisión sin permisos para invalidar la escritura inicial pendiente.

Los módulos permitidos son `CATALOG`, `SALES`, `EXPENSE` y `FINANCE`.
Cada permiso concede exactamente el par módulo y alcance. Omitir alcance
concede `READ`. Para conceder ambos alcances en un módulo envíe los dos pares
explícitamente. Las comprobaciones empresariales de Company consultan elegibilidad
y el permiso activo exacto en cada llamada, con el asesor autenticado y la empresa
del recurso. S2-314 abordará las rutas públicas de acceso del asesor.

El contrato OpenAPI se publica en `/v3/api-docs`. Una solicitud inválida
devuelve **400**, un JWT ausente o inválido **401**, un actor no autorizado o
destinatario no elegible **403**, y un actor REGULAR sin empresa **404**.
Identity devuelve la misma respuesta para un destinatario inexistente, inactivo
o de otro tipo, por lo que las tres condiciones devuelven **403**. Un fallo al
verificar autorización devuelve **503**.

### Credenciales de elegibilidad

Company llama a Identity directamente por el origen configurado en
`IDENTITY_SERVICE_URL`. Configure el mismo emisor mediante `ADVISOR_ELIGIBILITY_ISSUER` en Company y
`IDENTITY_ADVISOR_ELIGIBILITY_ISSUER` en Identity. Monte
`ADVISOR_ELIGIBILITY_PRIVATE_KEY_LOCATION=file:/ruta/clave-pkcs8.pem` en Company y
`IDENTITY_ADVISOR_ELIGIBILITY_PUBLIC_KEY_LOCATION=file:/ruta/clave-publica.pem` en Identity.
Monte la clave privada como secreto y use un par de claves RSA dedicado.
Company emite JWT técnicos RS256 de 60 segundos con el sujeto, audiencia, tipo
y alcance de S2-451. Se usa HTTPS fuera de `local` y `test`, con 500 ms
de conexión, 2 s de respuesta total y hasta 20 consultas simultáneas.

Identity solo registra el endpoint de elegibilidad con perfil `local` o `test`.
La activación productiva requiere habilitar el contrato en Identity y el
despliegue JDBC/Flyway de Company. Sin elegibilidad verificable el PUT devuelve
**503** y conserva el estado anterior. GET y DELETE no consultan la elegibilidad
del destinatario para que el OWNER pueda retirar accesos de cuentas deshabilitadas.

Flyway conserva una revisión por empresa y asesor y marca los permisos revocados
en base de datos. La auditoría S2-315 añade un evento por par cambiado en la
misma transacción, con el destinatario en `targetUserId` y el par en
`advisorPermission`. Si un OWNER intenta conceder acceso a un destinatario que
Identity no puede confirmar como elegible mediante una respuesta negativa, la
auditoría registra el intento rechazado sin identificar al destinatario.

## Equipo empresarial (S2-455)

Todas las rutas de equipo bajo `/api/v1/companies/{companyId}` tienen una
variante compatible bajo `/api/v1/companies/me`. Con un path explícito,
`X-Company-Id`, si se envía, debe coincidir. Identity confirma que la cuenta
autenticada es REGULAR y está activa antes de resolver el contexto.

| Método y subruta | Contrato |
| --- | --- |
| POST `/invitations` | Crea una invitación. Requiere nombre de hasta 160 caracteres, email y rol OPERATOR o VIEWER. `replace` es opcional y por defecto es false. Devuelve 201 con nombre, correo normalizado, rol, estado y vencimiento. |
| GET `/invitations?page=0&size=20` | Array de invitaciones pendientes vigentes con nombre, email, rol, estado y fechas. Acceso de OWNER y ADMIN. |
| DELETE `/invitations/{invitationId}` | Revoca una invitación pendiente de la empresa y devuelve 204. |
| GET `/users` | Conserva el array histórico de identificadores, rol y estado para lectores autorizados. |
| GET `/users?view=page` | Página de administración con `content`, `page`, `size`, `totalElements` y `totalPages`. Cada fila incluye ID de membresía, ID de usuario, nombre, email, rol y estado. |
| GET `/users/all` | Array con las mismas filas, filtros y orden que la página. Devuelve 400 con `MEMBER_LIST_LIMIT_EXCEEDED` si hay más de 500 coincidencias. |
| PATCH `/members/{userId}/role` | Cambia el rol según la política vigente. |
| PATCH `/members/{userId}/status` | Recibe `{"status":"SUSPENDED"}` o `{"status":"ACTIVE"}` para suspender o reactivar. |
| DELETE `/members/{userId}` | Revoca el acceso de la membresía seleccionada con 204. |

Ambas listas enriquecidas requieren OWNER o ADMIN activos de la empresa. Admiten
`search` (subcadena de nombre o email, hasta 160 caracteres, sin distinguir
mayúsculas y distinguiendo acentos), `status=ALL|ACTIVE|INACTIVE` (también acepta los filtros históricos SUSPENDED y REVOKED),
`role=OWNER|ADMIN|OPERATOR|VIEWER`, `page` desde 0, `size` de 1 a 100
(20 por defecto) y `sort=membershipId,asc|membershipId,desc`. El UUID de la
membresía define un orden total. Company aplica rol y estado en SQL. Sin texto
hace un conteo y pagina en SQL antes de enriquecer. Con texto recorre el conjunto
filtrado por cursor UUID en lotes de hasta 100, consulta Identity una vez por
lote, calcula el total de coincidencias y conserva solo la página pedida.
La consulta mantiene una instantánea de membresías y lee datos de Identity
vigentes en cada lote. La actualización concurrente de datos personales entre
lotes puede cambiar los resultados. Una respuesta de Identity incompleta o
indisponible devuelve 503. `status` proyecta SUSPENDED y REVOKED a INACTIVE.
`membershipStatus` conserva el estado real. Las invitaciones pendientes se consultan mediante `/invitations`.

La aceptación usa `POST /api/v1/invitations/{token}/accept` con la empresa
obtenida de la invitación persistida. Company exige que Identity confirme que
el correo actual está verificado y que coincida con el de la invitación.
El nombre de la invitación solo se conserva en Company. Una cuenta que ya
pertenece a otra empresa puede aceptar si el modo multiempresa está activado.
Una membresía activa o suspendida no admite otra alta. Una revocada puede
reincorporarse con una invitación emitida después de la revocación. Se conserva
el ID histórico, se eliminan concesiones explícitas y se aplica el rol de la
nueva invitación. La protección del último OWNER activo se aplica también a
suspensiones. Aceptación, membresía y auditoría se confirman en la misma
transacción. Para aceptar, Company toma el bloqueo por usuario, seguido por
el bloqueo de cambios de la empresa y el de la fila de invitación. Creación,
reemplazo y revocación de invitaciones comparten el bloqueo de empresa.

### Directorio interno y entrega

Configura `MEMBER_DIRECTORY_ISSUER` y
`MEMBER_DIRECTORY_PRIVATE_KEY_LOCATION=file:/ruta/clave-pkcs8.pem` en Company,
y la clave pública separada en Identity. Usa el origen directo de Identity
por HTTPS en un despliegue remoto. El [contrato interno](../identity-service/README.md#directorio-interno-de-miembros-s2-455)
describe credenciales, tamaño de lote y verificación.

`InvitationDelivery` recibe el token de 32 bytes después del commit. La base
guarda SHA-256 y el TTL por defecto es PT72H. `replace=true` revoca la
invitación pendiente anterior y genera un token nuevo en la misma transacción.
El token se consume una vez. Las invitaciones expiradas, revocadas y consumidas
devuelven 409 al destinatario verificado. El 201 confirma la creación de la
invitación. Falta integrar un proveedor de entrega autorizado que construya
el enlace HTTPS y lo envíe directamente al buzón destinatario. El adaptador de
captura vive en pruebas. Para el despliegue, configura el proxy, logs de
acceso y trazas para omitir o anonimizar la ruta de aceptación antes de
exportarla. Usa la plantilla de ruta en métricas y excluye cuerpos y cabeceras
de autenticación de la telemetría.

La verificación de correo en Identity sigue pendiente. Una sesión de login o
la posesión del token de invitación no sustituye esa evidencia de Identity.
Hasta disponer del flujo de verificación, Identity devuelve
`emailVerified: false` y Company deniega la aceptación con 403. El smoke
controlado de Company usa evidencia de prueba y el smoke de Identity comprueba
el contrato con registro real. El smoke público de aceptación completa queda
pendiente de verificación y entrega.
