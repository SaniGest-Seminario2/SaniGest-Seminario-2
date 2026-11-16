# Plantilla base para servicios Spring Boot

Esta convención define la base de los servicios de negocio de SaniGest. El
`company-service` es la implementación de referencia ejecutable; los demás
directorios se incorporan al reactor Maven únicamente cuando su ticket de
dominio entregue un esqueleto propio.

## Crear un módulo nuevo

1. Crear `services/<dominio>-service` y un `pom.xml` hijo de
   `com.sanigest:sanigest-parent`.
2. Añadir Spring Web, Validation, Data JPA y Actuator. Agregar Flyway y el driver
   de la base únicamente cuando el servicio sea propietario de datos.
3. Crear una clase `<Dominio>ServiceApplication` con `@SpringBootApplication` en
   `com.sanigest.<dominio>`. Ese paquete raíz limita el component scan al
   servicio y evita importar componentes de otro dominio.
4. Copiar solamente la configuración técnica de `application.yml`,
   `application-local.yml` y `application-test.yml`; cambiar el nombre de la
   aplicación, base y variables que pertenezcan al módulo.
5. Añadir el módulo al reactor raíz y crear sus pruebas antes de eliminar el
   README que reserva el directorio.

El API Gateway aplica la misma configuración operativa, pero su ticket puede
reemplazar Spring Web/JPA por Spring Cloud Gateway y configuración reactiva. No
debe adquirir persistencia ni reglas de los servicios de negocio.

## Estructura obligatoria

```text
com.sanigest.<dominio>
├── api/             Controladores HTTP y DTOs de transporte
├── application/     Casos de uso y orquestación
├── domain/          Entidades, reglas, eventos y puertos del dominio
└── infrastructure/  Adaptadores de base de datos, mensajería y sistemas externos
```

Las dependencias apuntan hacia el dominio: `api` e `infrastructure` pueden usar
`application`; `application` puede usar `domain`; `domain` no depende de Spring,
JPA, HTTP ni clases de otro servicio. No se copian entidades, repositorios o
reglas entre módulos. Un contrato común se extrae a `libraries` solo cuando
existen al menos dos consumidores y el ADR-001 permite compartirlo.

## Configuración y secretos

- `application.yml` contiene valores comunes seguros, el puerto
  `${SERVER_PORT:8080}` y Actuator limitado a `health` e `info`.
- El perfil `local` toma URL, usuario y contraseña desde variables de entorno.
  Las credenciales no tienen valores predeterminados en el repositorio.
- El perfil `test` usa una base H2 en memoria y nunca conecta con PostgreSQL del
  desarrollador.
- El perfil predeterminado permite arrancar el esqueleto y sus endpoints
  operativos sin infraestructura local. Los adaptadores persistentes se activan
  al usar `local` o `test`.

## Pruebas mínimas

- `*Test`: pruebas unitarias rápidas, ejecutadas por Surefire en `mvn test`.
- `*IT`: integración y regresión de configuración, migraciones y contratos HTTP,
  ejecutadas por Failsafe en `mvn verify`.
- `scripts/qa/smoke-company-service.sh`: arranca el JAR real, consulta health e
  info y comprueba que endpoints sensibles no estén publicados.

Para validar la referencia completa:

```bash
mvn -B clean verify
sh scripts/qa/smoke-company-service.sh
```

Para desarrollo con PostgreSQL local, cargar `.env` y ejecutar:

```bash
mvn -pl services/company-service -am spring-boot:run \
  -Dspring-boot.run.profiles=local
```
