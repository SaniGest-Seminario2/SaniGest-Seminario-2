# Librerías compartidas

Los módulos requieren al menos dos consumidores. `common-security` contiene
la validación JWT que usan Gateway, Identity y Company.

Se permiten contratos técnicos versionados y utilidades pequeñas, por ejemplo
`common-web`, `common-security` o `common-events`. Se prohíben entidades JPA,
repositorios, DTO de dominio y reglas de negocio compartidas entre servicios,
conforme al ADR-001 de `S2-303`.
