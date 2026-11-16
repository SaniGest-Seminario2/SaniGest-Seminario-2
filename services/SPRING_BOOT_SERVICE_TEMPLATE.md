# Plantilla base para servicios Spring Boot

Esta convención define la base de los servicios de negocio de SaniGest. La
implementación neutral y ejecutable vive en `templates/spring-boot-service`;
`company-service` es un consumidor de la convención, no la plantilla. Los demás
directorios se incorporan al reactor Maven únicamente cuando su ticket de
dominio entregue un esqueleto propio.

## Crear un módulo nuevo

1. Copiar `templates/spring-boot-service` a `services/<dominio>-service`.
2. Reemplazar `spring-boot-service-template`, `ServiceTemplateApplication` y
   `com.sanigest.template` por los nombres del nuevo servicio y dominio.
3. Mantener Spring Web, Validation, Data JPA y Actuator. Agregar Flyway y las
   migraciones únicamente cuando el servicio sea propietario de datos.
4. Mantener la clase `<Dominio>ServiceApplication` con `@SpringBootApplication` en
   `com.sanigest.<dominio>`. Ese paquete raíz limita el component scan al
   servicio y evita importar componentes de otro dominio.
5. Cambiar el nombre de aplicación y las variables que pertenezcan al módulo,
   conservando los perfiles `local` y `test` y la política segura de Actuator.
6. Añadir el módulo al reactor raíz y adaptar sus pruebas antes de eliminar el
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
- `scripts/qa/smoke-spring-boot-service.sh`: smoke reutilizable que arranca un
  JAR real, consulta health e info y comprueba que endpoints sensibles no estén
  publicados.

Para validar la referencia completa:

```bash
mvn -B clean verify
sh scripts/qa/smoke-spring-boot-service.sh \
  templates/spring-boot-service/target/spring-boot-service-template-0.1.0-SNAPSHOT.jar \
  spring-boot-service-template
```

La plantilla arranca sin infraestructura externa desde la raíz:

```bash
mvn -pl templates/spring-boot-service -am spring-boot:run
```

Para usar PostgreSQL local, definir `DATABASE_URL`, `DATABASE_USERNAME` y
`DATABASE_PASSWORD`, y activar el perfil `local`. Cada ticket de dominio debe
renombrar esas variables cuando necesite aislarlas por servicio.
