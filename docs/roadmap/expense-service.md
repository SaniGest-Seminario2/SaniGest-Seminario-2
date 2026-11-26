# Expense Service

S2-342 incorpora el servicio al reactor Maven y a Docker Compose. Su base
`sanigest_expense` usa un rol propio y migraciones Flyway. La aplicación
comparte la validación JWT del monorepo y expone sus sondas de Actuator.

```sh
docker compose --env-file .env --profile apps up -d --build expense-service
curl --fail http://localhost:8087/actuator/health/readiness
```

El [contrato funcional](../SaniGest_T1001_Contrato_Funcional_v0.8.md) asigna
`/api/v1/expenses` y `/api/v1/payables` a Expense. Las operaciones de registro,
consulta y pago se incorporan en S2-343 a S2-348 antes de habilitar sus rutas.
Los puertos de aplicación definen la resolución de proveedores y artículos
en Catalog y la coordinación de almacenes y entradas con Inventory (S2-465).
S2-466 incorpora adjuntos privados. Finance recibe hechos de pago confirmado
cuando estén implementados los pagos.
