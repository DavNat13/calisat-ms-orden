# Changelog - calisat-ms-orden

## [2.0.0] - 2026-09-23

### BREAKING CHANGE
- Versión pom.xml incrementada a 2.0.0 (fase B: integración entre microservicios)
- `OrdenService` ahora exige en su constructor los nuevos clientes HTTP `CarritoClient`, `InventarioClient`, `EnviosClient` y `NotificacionesClient` (paquete `com.califorge.msorden.client`); cualquier construcción manual del servicio debe inyectarlos
- La creación de orden pasa a reservar stock, publicar `ORDEN_CONFIRMADA` y vaciar el carrito (operaciones best-effort); la cancelación libera reservas y publica `ORDEN_CANCELADA`; los cambios de estado confirman stock (PAGADA) y crean envío (EN_PREPARACION/ENVIADA)

### Added
- Paquete `client` con clientes RestTemplate aislados: `CarritoClient`, `InventarioClient` (reservar/liberar/confirmar), `EnviosClient` y `NotificacionesClient`, sin service discovery
- URLs base por variable de entorno con default localhost: `CALISAT_CARRITO_URL` (8084), `CALISAT_INVENTARIO_URL` (8083), `CALISAT_ENVIOS_URL` (8086), `CALISAT_NOTIFICACIONES_URL` (8087)
- `RestTemplateConfig` con el bean `RestTemplate` compartido por los clientes
- Saga minimal con compensación: reservar al crear → confirmar al pagar → liberar al cancelar (inventario)
- Vaciado del carrito tras confirmar el checkout (orden → carrito)
- Creación de envío con snapshot de dirección al pasar a EN_PREPARACION/ENVIADA (orden → envios)
- Eventos `ORDEN_CONFIRMADA` y `ORDEN_CANCELADA` hacia `/api/v1/notificaciones/eventos` con cabecera `Idempotency-Key` (orden → notificaciones)
- Degradación elegante: cada integración es try/catch best-effort y jamás interrumpe el flujo principal de la orden
- Tests de clientes con `RestTemplate` mockeado (`CarritoClientTest`, `InventarioClientTest`, `EnviosClientTest`, `NotificacionesClientTest`) y tests de servicio que verifican las llamadas de integración

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

[2.0.0]: https://github.com/DavNat13/calisat-ms-orden/compare/v1.3.0...v2.0.0
[1.3.0]: https://github.com/DavNat13/calisat-ms-orden/releases/tag/v1.3.0
