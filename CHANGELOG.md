# Changelog - calisat-ms-orden

## [1.3.0] - 2026-09-23

### Added
- Microservicio calisat-ms-orden con Spring Boot 4.1.0 y Java 21 (puerto 8085)
- Docker Compose con PostgreSQL 15 (`calisat_orden`, puerto host 5435) y app Spring Boot
- Entidades JPA Orden (snapshot de dirección e idempotency_key única), OrdenItem (snapshot de precios) y OrdenEvento (auditoría append-only)
- Máquina de estados PENDIENTE → PAGADA → EN_PREPARACION → ENVIADA → ENTREGADA / CANCELADA / FALLO_PAGO
- OrdenService con creación idempotente, totales, historial propio paginado, transiciones validadas y cancelación
- Endpoints: POST/GET /api/v1/ordenes, GET /api/v1/ordenes/{id}, GET /api/v1/ordenes/mis-ordenes, PUT /api/v1/ordenes/{id}/estado, POST /api/v1/ordenes/{id}/cancelar (header Idempotency-Key)
- SecurityConfig con validacion JWT de Azure Entra ID (issuer + audience) y CORS; sin RBAC (solo autenticacion)
- GlobalExceptionHandler con manejo de errores de negocio y validacion
- Tests de servicio (OrdenServiceTest)
- Health check via Spring Actuator
