# Acceso empresarial entre servicios

`CompanyAccess.membership(scope)` consulta en Company la membresía activa del
usuario autenticado. Devuelve su empresa, rol actual y si existe una concesión
explícita para la combinación módulo y alcance. Cada consumidor decide cómo
aplicar esos datos a su operación, incluso desde un caso de uso protegido con
Spring Method Security. Si una operación ya tiene empresa, el consumidor debe
compararla con la empresa devuelta antes de acceder al recurso.

`CompanyAccess.delegated(companyId, scope)` verifica la concesión actual del
asesor para una empresa concreta. El consumidor obtiene la empresa de su
recurso o de la selección que debe verificar. Company aplica la elegibilidad
actual de Identity, la empresa activa y la concesión exacta de S2-313.
`READ` y `WRITE` son alcances distintos. Se admiten `CATALOG`, `SALES`,
`EXPENSE` y `FINANCE`.

## Contrato interno v1

`GET /internal/v1/company-access` se sirve en el origen privado de Company.
La ruta queda fuera del Gateway y del OpenAPI público. El encabezado
`Authorization` lleva un JWT técnico RS256 con un emisor y clave dedicados
por módulo, tipo `sanigest-service+jwt`, audiencia exclusiva
`sanigest-company-access` y una vigencia máxima de 60 segundos. Los claims
firmados incluyen `sub` (UUID del actor), `module` y `scope`. La consulta
delegada incluye además `company_id` (UUID a verificar). Company vincula la
clave al módulo configurado y valida los claims antes de resolver el acceso.

Para membresía, la librería toma de Spring Security el JWT de sesión ya
validado y lo envía en `X-Company-User-Assertion`. Company revalida ese JWT,
vincula su sujeto a la aserción técnica y usa la consulta existente de Identity
para verificar que la cuenta REGULAR sigue activa. El destino delegado utiliza
solo la aserción técnica y la elegibilidad dinámica. Los parámetros de URL y
encabezados externos de usuario o empresa carecen de efecto sobre la decisión.
Mantener ambos encabezados de credenciales excluidos de logs y trazas de
payload. La conexión usa HTTPS salvo durante desarrollo y pruebas locales.

| Respuesta | Significado |
| --- | --- |
| `200` | JSON con `userId`, `companyId`, `module`, `scope` y `mode`. Para `MEMBERSHIP` incluye `role` y `explicitGrant` booleano (permiso `<MODULE>_<SCOPE>`). Para `DELEGATED` confirma la empresa solicitada. |
| `403` | Denegación efectiva. La librería devuelve un resultado vacío o falso. |
| `401`, `404`, `5xx` y restantes estados inesperados | Error técnico. La librería lanza `CompanyAccessUnavailable`. |
| Timeout, red caída, cuerpo inválido o identidad, módulo, alcance o empresa delegada discordantes | Error técnico. La librería lanza `CompanyAccessUnavailable`. |

Para membresía `SALES / WRITE`, Company incluye `companyName` desde la empresa
activa asociada a `companyId`. Sales verifica la autorización antes de utilizar
el dato como snapshot. El campo es opcional durante el despliegue de Company.

Sin caché de decisiones y con `Cache-Control: no-store` en la respuesta de
Company. Una revocación confirmada se aplica desde la siguiente consulta que
lea el estado actual. Una operación ya autorizada puede seguir en curso.

Límites predeterminados de Catalog de 500 ms para conectar, 2 s para la respuesta
completa y 20 solicitudes concurrentes por cliente. Sin reintentos ni
redirecciones. La concurrencia acotada y el timeout contienen el impacto de la
dependencia. El cliente conserva un trace ID W3C válido, genera uno cuando
corresponde y publica la observación `sanigest.company.access` y logs técnicos
con trace ID y motivo, sin incluir credenciales ni cuerpos.

## Configuración para Catalog

En Company se registra la clave pública de Catalog. Para otros consumidores
se añade una entrada con el módulo correspondiente y credenciales diferentes.

```yaml
sanigest:
  company-access:
    callers:
      catalog:
        issuer: catalog-company-access
        public-key: file:/run/secrets/catalog-company-access-public.pem
```

En Catalog se habilita el adaptador y se monta la clave privada PKCS#8
correspondiente. Company e Identity deben mantener operativas sus consultas
internas de sesión y de elegibilidad de asesor, según corresponda.

```yaml
sanigest:
  company-access:
    enabled: true
    origin: https://company.internal
    issuer: catalog-company-access
    private-key-location: file:/run/secrets/catalog-company-access-private.pem
    connect-timeout: 500ms
    response-timeout: 2s
```

El alta actual de clientes obtiene la empresa exclusivamente de la membresía.
Catalog concede el alta a OWNER y ADMIN, y a OPERATOR con concesión explícita
de escritura para Catalog. VIEWER queda denegado. El contrato público de alta
no selecciona una empresa delegada. Una denegación se traduce a `403`. Con la
integración deshabilitada o la autoridad indisponible responde `503` y se
evita la escritura.
