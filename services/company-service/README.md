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
