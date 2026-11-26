# Inventory Service

S2-460 incorpora el contexto propietario de almacenes, ubicaciones por posición,
control de existencias, balances físicos y kardex. Catalog conserva la ficha
comercial y la unidad de medida. Company conserva las membresías. S2-465 incorpora
reservas, recepciones, reversión y verificación de operaciones comerciales
pendientes antes del cierre de almacenes.

[OpenAPI](src/main/resources/static/inventory-openapi.json) se sirve como
`GET /inventory-openapi.json` directamente desde Inventory. Actuator publica
`/actuator/info` y `/actuator/health/{liveness,readiness}`. Readiness incluye PostgreSQL.
Los fallos remotos afectan las operaciones que necesitan una validación vigente.

## Autorización

Todas las rutas de negocio requieren JWT y un solo `X-Company-Id` UUID. Inventory
revalida la membresía mediante Company v2, incluso al reproducir una operación.
Los permisos de Catalog se verifican por separado cuando se consulta el artículo.

| Operación | OWNER / ADMIN | VIEWER | OPERATOR | ADVISOR |
| --- | --- | --- | --- | --- |
| Leer | Membresía `INVENTORY/READ` | Membresía `INVENTORY/READ` | Membresía con grant explícito `INVENTORY_READ` | Sin delegación |
| Crear, editar y cambiar estado de almacén | Membresía `INVENTORY/WRITE` | Denegado | Denegado | Sin delegación |
| Configurar existencias | Membresía `INVENTORY/WRITE` | Denegado | Denegado | Sin delegación |
| Ajustar existencias, incluso salidas residuales | Membresía `INVENTORY/WRITE` | Denegado | Denegado | Sin delegación |

La política no interpreta `INVENTORY_WRITE` como una colección de permisos
granulares. Company valida una credencial RSA dedicada para Inventory, con
emisor `inventory-company-access` y audiencia del protocolo v2.

## Almacenes, stock y movimientos

- El código de almacén tiene 1–32 caracteres y se normaliza a mayúsculas. Admite
  letras ASCII, dígitos, guion y guion bajo. El nombre admite 120 caracteres y la
  ubicación 240. PATCH conserva los campos omitidos. `location: null` limpia
  la ubicación. Se puede enviar la revisión leída para detectar ediciones obsoletas.
- PUT reemplaza la configuración. `trackingEnabled` es obligatorio, el mínimo
  omitido o nulo equivale a cero, y el máximo y ubicación omitidos o nulos se
  limpian. `revision` es opcional. Una cantidad inicial omitida, nula o cero
  consume la oportunidad de inicializar al habilitar por primera vez. Una
  cantidad positiva requiere `Idempotency-Key`. La revisión y la inicialización
  pertenecen a la posición empresa–artículo–almacén.
- Los decimales son cadenas con hasta tres decimales y se almacenan como
  `numeric(15,3)`. Una unidad indivisible exige un valor entero. Un producto histórico
  sin información de unidad debe completarse en Catalog antes de escribir en
  Inventory. Si cambian el código o la divisibilidad tras registrar movimientos,
  Inventory bloquea escrituras hasta resolver la incompatibilidad. El kardex
  conserva su unidad histórica. Para un artículo inactivo se permiten ajustes
  de salida auditados, con el mismo permiso de ajuste y un motivo de hasta 500
  caracteres.
- Una posición sin configuración devuelve `configured: false` y cantidades
  nulas. El detalle verifica el artículo en Catalog y devuelve `NOT_TRACKED`
  para PRODUCT o `NOT_APPLICABLE` para SERVICE. Una configuración guardada con seguimiento deshabilitado
  tiene `configured: true`. Para posiciones controladas, `OUT` significa
  disponible igual a cero, `LOW` indica disponible mayor que cero y menor o igual
  al mínimo, y `AVAILABLE` indica disponible superior al mínimo. El máximo es
  informativo. El estado operativo del almacén se consulta en `/warehouses/{id}`.
- Los listados aplican filtros antes de paginar, con 20 elementos por defecto,
  hasta 100. Para obtener posiciones de una página de Catalog, usa `warehouseId`,
  `catalogItemIds` (hasta 100 UUID separados por coma) y un `size` suficiente.
  El lote devuelve las posiciones configuradas del almacén elegido e incluye
  `warehouseName`. La búsqueda de stock usa SKU y nombre obtenidos en la última
  escritura validada por Catalog. `catalogVerifiedAt` indica la frescura de la
  copia local. Las escrituras consultan al propietario. El resumen cuenta
  combinaciones artículo–almacén e incluye las configuraciones explícitamente
  deshabilitadas. No agrega cantidades de unidades diferentes.
- Los ajustes requieren `Idempotency-Key` y un motivo. `dateFrom` es inclusiva y
  `dateTo` exclusiva. Ambas aceptan instantes ISO 8601. `sequence` define el
  orden exacto del kardex dentro de una posición. Un reintento con la misma
  clave y solicitud devuelve el resultado original, aunque haya nuevos ajustes.

## Persistencia y transacciones

Flyway crea el esquema `inventory` en la base `sanigest_inventory`. Se utiliza
JDBC para hacer explícitos los predicados empresariales, los bloqueos y las
consultas filtradas. Configuración y balance comparten una fila en
`stock_positions`, con modelos de dominio independientes. Así comparten
identidad, revisión y restricciones SQL. `available` se deriva de físico menos
reservado. El kardex `stock_movements` es append-only, con unicidad por efecto,
secuencia e inicialización. Las claves foráneas incluyen la empresa.

Las consultas remotas se ejecutan antes de abrir la transacción de escritura.
Toda mutación de una posición toma primero el lock de su almacén. El cierre usa
ese mismo lock para comprobar que no hay físico ni reservas. Después del lock
se reclama la clave idempotente en PostgreSQL, con ámbito empresa, actor,
operación y clave. Este orden también protege la primera inserción cuando la
posición aún no existe. La posición, el movimiento y la respuesta idempotente
se confirman juntos. La implementación futura multilínea deberá ordenar todos
los almacenes por empresa e ID antes de tomar posiciones por artículo.

`ReconcileStock` compara el balance persistido con entradas menos salidas por
posición, en una única consulta consistente. Registra la diferencia y devuelve
ambos valores sin reparar los datos. La reconstrucción no consulta Catalog.

## Desarrollo y actualización

Configura credenciales de desarrollo mediante la
[guía de contexto empresarial](../../docs/development/company-context.md). La
instalación local utiliza los puertos 8086 para Inventory y 8085 para el Gateway
del stack integrado. Si la red local está marcada como interna, configura
`SANIGEST_NETWORK_INTERNAL=false` para las consultas desde el host. En desarrollo sin Compose, el perfil `local` conecta con
Company en 8080 y Catalog en 8083. Define `INVENTORY_DB_HOST`,
`INVENTORY_DB_PASSWORD`, `JWT_ISSUER`, `JWT_AUDIENCE`, `JWT_PUBLIC_KEY_LOCATION`
e `INVENTORY_COMPANY_ACCESS_PRIVATE_KEY_LOCATION`. Company debe incluir el
emisor y la clave pública en su mapa de callers. Fuera de `local` y `test`,
configura orígenes HTTPS explícitos para Company y Catalog.

```sh
mvn -B -pl services/inventory-service -am verify
docker compose --env-file .env.example --profile apps build inventory-service
```

En un volumen existente, incorpora `INVENTORY_DB_PASSWORD` al archivo de
entorno y actualiza el contenedor de PostgreSQL antes de ejecutar el bootstrap
dirigido. Este comando se puede repetir y administra únicamente el rol, la base
y el esquema público de Inventory. Los scripts de init automáticos son para
volúmenes nuevos.

```sh
docker compose --profile infra up -d --wait postgres
docker compose exec -T postgres /docker-entrypoint-initdb.d/10-create-service-databases.sh inventory
docker compose --profile apps up -d --build --wait inventory-service
```

Con JWT, empresa, producto y almacén del entorno local:

```sh
base=http://127.0.0.1:8086/api/v1
curl -fsS "$base/warehouses" -H "Authorization: Bearer $TOKEN" -H "X-Company-Id: $COMPANY_ID" \
  -H 'Content-Type: application/json' -d '{"code":"ALM-001","name":"Principal"}'

curl -fsS -X PUT "$base/inventory/stock/$ITEM_ID/$WAREHOUSE_ID" \
  -H "Authorization: Bearer $TOKEN" -H "X-Company-Id: $COMPANY_ID" \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: initial-1' \
  -d '{"trackingEnabled":true,"minimumStock":"5.000","initialQuantity":"20.000"}'

curl -fsS "$base/inventory/adjustments" -H "Authorization: Bearer $TOKEN" -H "X-Company-Id: $COMPANY_ID" \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: adjustment-1' \
  -d "{\"catalogItemId\":\"$ITEM_ID\",\"warehouseId\":\"$WAREHOUSE_ID\",\"direction\":\"OUT\",\"quantity\":\"3\",\"reason\":\"Conteo físico\"}"

curl -fsS "$base/inventory/movements?warehouseId=$WAREHOUSE_ID&order=ASC" \
  -H "Authorization: Bearer $TOKEN" -H "X-Company-Id: $COMPANY_ID"
```

El smoke del stack completo vive en
[`smoke-multi-company.py`](../../infrastructure/qa/smoke-multi-company.py).
La suite Maven etiqueta smoke y regresión con las convenciones de Failsafe.
Para ejecutar el flujo con Identity, Company, Catalog, Inventory y Gateway,
usa `sh infrastructure/qa/run-smoke-multi-company.sh`.
