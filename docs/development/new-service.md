# Incorporar un servicio

1. Verifica la responsabilidad y la propiedad de datos en el
   [contrato objetivo v0.8](../SaniGest_T1001_Contrato_Funcional_v0.8.md) y en
   [ADR-002](../adr/ADR-002-evolucion-arquitectonica-multiempresa-inventario-finanzas.md).
2. Crea `services/<dominio>-service` con un POM que herede de `sanigest-parent`
   y agrega su ruta a los módulos del POM raíz. Usa el POM de
   [Identity](../../services/identity-service/pom.xml) como referencia para las
   dependencias, perfiles y el empaquetado de Spring Boot.
3. Ubica la clase principal de Spring Boot en `com.sanigest.<dominio>`. Mantén
   los controladores HTTP, sus DTO y el mapeo de errores en `api/controller`,
   `api/dto` y `api/error`. Agrupa los flujos por capacidad en
   `application/usecase`, sus datos de entrada y salida en `application/dto`
   y sus errores en `application/exception`. Define los contratos salientes en
   `application/port/out`, agrupados según la dependencia que representan.
   Implementa esos contratos en `infrastructure` con adaptadores de
   persistencia, integración, mensajería o seguridad según la capacidad. Conserva el modelo y sus
   reglas en `domain/model` y los enums en `domain/enums`. Coloca las
   constantes compartidas junto al contrato al que pertenecen.
   El caso de uso define el límite transaccional cuando coordina varias
   operaciones de persistencia. El adaptador puede delimitar la transacción
   de una operación atómica de su puerto y traduce los errores del proveedor.
   Refleja los paquetes en las pruebas unitarias y coloca las pruebas de
   integración en `integration`.
   Consulta [Company](../../services/company-service/README.md) (JDBC),
   [Identity](../../services/identity-service/README.md) y
   [Catalog](../../services/catalog-service/README.md) (Hibernate/JPA).
4. Usa `src/main/resources/application.yml` para la configuración base y
   `src/test/resources` para los recursos exclusivos de pruebas. Si el servicio
   persiste datos, agrega sus migraciones Flyway bajo `db/migration` y configura
   la conexión con su propio usuario de base de datos.
5. Ejecuta `mvn -B -pl services/<dominio>-service -am verify`. Para habilitar
   la verificación por servicio, añade un workflow que invoque a
   [Service verification](../../.github/workflows/reusable-service-ci.yml)
   cuando el módulo cuente con pruebas etiquetadas `smoke` y `regression`.
6. Si el servicio se ejecuta en Compose, añade su POM al contexto de
   [la imagen compartida](../../infrastructure/docker/service.Dockerfile) y
   configura el argumento `SERVICE`, las dependencias y la comprobación de salud
   en [docker-compose.yml](../../docker-compose.yml). Actualiza la
   [validación de Compose](../../infrastructure/qa/validate-compose.sh) y añade
   la imagen a la [verificación de infraestructura](../../.github/workflows/ci-infrastructure.yml).

El [catálogo de librerías](../../libraries/README.md) incluye validación JWT
y el contrato técnico de autorización empresarial. Conserva los modelos y
reglas de negocio dentro de cada servicio.

En Expense, Finance e Inventory aplica la [guía de contexto empresarial](company-context.md).
Requiere el selector en rutas empresariales, autoriza cada operación contra
Company y usa el tenant autorizado para consultas, eventos e idempotencia.
