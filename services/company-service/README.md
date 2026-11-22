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
[contrato funcional](../../docs/SaniGest_T1001_Contrato_Funcional_v0.7.md).

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
