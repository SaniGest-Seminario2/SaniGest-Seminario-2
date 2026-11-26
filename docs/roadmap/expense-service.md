# Expense Service

S2-342 incorpora el servicio al reactor Maven y a Docker Compose. Su base
`sanigest_expense` usa un rol propio y migraciones Flyway. La aplicación
comparte la validación JWT del monorepo y expone sus sondas de Actuator.

```sh
docker compose --env-file .env --profile apps up -d --build expense-service
curl --fail http://localhost:8087/actuator/health/readiness
```

[S2-343] habilita el alta `POST /api/v1/expenses` para OWNER y ADMIN con
membresía activa. Gateway habilita la ruta cuando se configura
`EXPENSE_SERVICE_URL`. Expense verifica la membresía con las credenciales
dedicadas de `EXPENSE_COMPANY_ACCESS_*` y valida las referencias con Catalog e
Inventory. La configuración de QA incluye estas conexiones.

El alta congela las líneas y sus totales en DOP. Una compra a crédito con saldo
positivo crea su obligación en la misma transacción. La recepción afecta solo
las posiciones con seguimiento activo en el almacén y usa la clave de cada
línea para que Inventory aplique la entrada una vez. Si la sincronización falla,
repite el POST con el mismo usuario, empresa, cuerpo e `Idempotency-Key`.
`attachmentId` reserva la referencia opcional para el flujo de adjuntos de
S2-466.

Consulta el [OpenAPI](../../services/expense-service/openapi-expenses.yaml) y el
[ejemplo Postman](../development/postman/SaniGest.postman_collection.json).

[S2-343]: https://devjoanvasquez-1791077375328.atlassian.net/browse/S2-343
