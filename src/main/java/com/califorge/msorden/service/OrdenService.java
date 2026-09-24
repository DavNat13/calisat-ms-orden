package com.califorge.msorden.service;

import com.califorge.msorden.client.CarritoClient;
import com.califorge.msorden.client.EnvioSolicitudDto;
import com.califorge.msorden.client.EnviosClient;
import com.califorge.msorden.client.InventarioClient;
import com.califorge.msorden.client.NotificacionEventoDto;
import com.califorge.msorden.client.NotificacionesClient;
import com.califorge.msorden.config.RabbitConfig;
import com.califorge.msorden.dto.OrdenCreateRequest;
import com.califorge.msorden.dto.OrdenItemRequest;
import com.califorge.msorden.dto.OrdenMensaje;
import com.califorge.msorden.exception.EstadoInvalidoException;
import com.califorge.msorden.exception.OrdenItemsVaciosException;
import com.califorge.msorden.exception.TransicionNoPermitidaException;
import com.califorge.msorden.model.EstadoOrden;
import com.califorge.msorden.model.Orden;
import com.califorge.msorden.model.OrdenEvento;
import com.califorge.msorden.model.OrdenItem;
import com.califorge.msorden.repository.OrdenEventoRepository;
import com.califorge.msorden.repository.OrdenItemRepository;
import com.califorge.msorden.repository.OrdenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Logica de negocio de las ordenes: creacion con idempotencia, consulta
 * propia (aislada por usuarioSub), maquina de estados y cancelacion.
 * Todas las operaciones de lectura de una orden filtran por el sub del
 * JWT para no filtrar datos entre usuarios (fase actual sin RBAC).
 *
 * <p>Fase B (integraciones, todas best-effort con try/catch): reserva de
 * stock en inventario al crear, confirmacion al pagar, liberacion al
 * cancelar, vaciado del carrito tras el checkout, creacion de envio al
 * preparar/enviar y eventos a notificaciones (ORDEN_CONFIRMADA /
 * ORDEN_CANCELADA). Ninguna falla interrumpe el flujo principal.</p>
 */
@Service
@Transactional
public class OrdenService {

    private static final Logger log = LoggerFactory.getLogger(OrdenService.class);

    /** Mapa de transiciones permitidas de la maquina de estados. */
    private static final Map<EstadoOrden, Set<EstadoOrden>> TRANSICIONES_PERMITIDAS = Map.of(
            EstadoOrden.PENDIENTE, EnumSet.of(EstadoOrden.PAGADA, EstadoOrden.CANCELADA, EstadoOrden.FALLO_PAGO),
            EstadoOrden.PAGADA, EnumSet.of(EstadoOrden.EN_PREPARACION, EstadoOrden.CANCELADA),
            EstadoOrden.EN_PREPARACION, EnumSet.of(EstadoOrden.ENVIADA),
            EstadoOrden.ENVIADA, EnumSet.of(EstadoOrden.ENTREGADA),
            EstadoOrden.CANCELADA, EnumSet.noneOf(EstadoOrden.class),
            EstadoOrden.FALLO_PAGO, EnumSet.noneOf(EstadoOrden.class),
            EstadoOrden.ENTREGADA, EnumSet.noneOf(EstadoOrden.class));

    private final OrdenRepository ordenRepository;
    private final OrdenItemRepository ordenItemRepository;
    private final OrdenEventoRepository ordenEventoRepository;
    private final CarritoClient carritoClient;
    private final InventarioClient inventarioClient;
    private final EnviosClient enviosClient;
    private final NotificacionesClient notificacionesClient;
    private final RabbitTemplate rabbitTemplate;

    public OrdenService(OrdenRepository ordenRepository,
                        OrdenItemRepository ordenItemRepository,
                        OrdenEventoRepository ordenEventoRepository,
                        CarritoClient carritoClient,
                        InventarioClient inventarioClient,
                        EnviosClient enviosClient,
                        NotificacionesClient notificacionesClient,
                        RabbitTemplate rabbitTemplate) {
        this.ordenRepository = ordenRepository;
        this.ordenItemRepository = ordenItemRepository;
        this.ordenEventoRepository = ordenEventoRepository;
        this.carritoClient = carritoClient;
        this.inventarioClient = inventarioClient;
        this.enviosClient = enviosClient;
        this.notificacionesClient = notificacionesClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Resultado de la creacion de una orden: la orden guardada y si es una
     * reutilizacion por idempotencia (true -> HTTP 200, false -> HTTP 201).
     */
    public record Creacion(Orden orden, boolean reutilizada) {}

    /**
     * Crea una orden del usuario con sus items (precios como snapshot del
     * request). Si la clave de idempotencia (header con prioridad o body)
     * ya existe, devuelve la orden previa sin crear otra.
     *
     * @param azureSub sub del usuario autenticado (dueño de la orden)
     * @param request items, direccion snapshot e idempotencyKey opcional del body
     * @param idempotencyKeyHeader valor del header Idempotency-Key (opcional)
     * @return la orden creada (reutilizada=false) o la existente (reutilizada=true)
     */
    public Creacion crear(String azureSub, OrdenCreateRequest request, String idempotencyKeyHeader) {
        String clave = (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank())
                ? idempotencyKeyHeader
                : request.idempotencyKey();

        if (clave != null && !clave.isBlank()) {
            Optional<Orden> previa = ordenRepository.findByIdempotencyKey(clave);
            if (previa.isPresent()) {
                return new Creacion(previa.get(), true);
            }
        }

        if (request.items() == null || request.items().isEmpty()) {
            throw new OrdenItemsVaciosException();
        }

        Orden orden = new Orden();
        orden.setUsuarioSub(azureSub);
        orden.setEstado(EstadoOrden.PENDIENTE);
        orden.setIdempotencyKey(clave == null || clave.isBlank() ? null : clave);
        orden.setDireccionCalle(request.direccionCalle());
        orden.setDireccionCiudad(request.direccionCiudad());
        orden.setDireccionPais(request.direccionPais());
        orden.setDireccionCodigoPostal(request.direccionCodigoPostal());

        List<OrdenItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrdenItemRequest item : request.items()) {
            OrdenItem entidad = new OrdenItem();
            entidad.setOrden(orden);
            entidad.setSku(item.sku());
            entidad.setNombreProducto(item.nombreProducto());
            entidad.setCantidad(item.cantidad());
            entidad.setPrecioUnitario(item.precioUnitario());
            BigDecimal subtotalItem = item.precioUnitario()
                    .multiply(BigDecimal.valueOf(item.cantidad()));
            entidad.setSubtotal(subtotalItem);
            subtotal = subtotal.add(subtotalItem);
            items.add(entidad);
        }
        orden.setSubtotal(subtotal);
        orden.setTotal(subtotal);

        ordenRepository.save(orden);
        for (OrdenItem item : items) {
            ordenItemRepository.save(item);
        }
        ordenEventoRepository.save(new OrdenEvento(orden, null, EstadoOrden.PENDIENTE, azureSub));

        integrarTrasCreacion(orden, items, azureSub);

        return new Creacion(orden, false);
    }

    /**
     * Lista las ordenes propias del usuario, paginadas y de mas reciente a
     * mas antigua.
     *
     * @param azureSub sub del usuario autenticado
     * @param pageable paginacion solicitada
     * @return pagina de ordenes del usuario
     */
    @Transactional(readOnly = true)
    public Page<Orden> listarPropias(String azureSub, Pageable pageable) {
        return ordenRepository.findByUsuarioSubOrderByFechaCreacionDesc(azureSub, pageable);
    }

    /**
     * Busca una orden por id filtrando por el dueño (usuarioSub del JWT);
     * si no pertenece al sub, devuelve vacio (404, sin fuga de datos).
     *
     * @param id identificador de la orden
     * @param azureSub sub del usuario autenticado
     * @return la orden si existe y es del usuario
     */
    @Transactional(readOnly = true)
    public Optional<Orden> buscarPorId(UUID id, String azureSub) {
        return ordenRepository.findByIdAndUsuarioSub(id, azureSub);
    }

    /**
     * Devuelve las lineas de una orden (snapshot de items).
     *
     * @param ordenId identificador de la orden
     * @return items de la orden
     */
    @Transactional(readOnly = true)
    public List<OrdenItem> itemsDe(UUID ordenId) {
        return ordenItemRepository.findByOrdenId(ordenId);
    }

    /**
     * Cambia el estado de una orden validando la maquina de estados.
     * Si el estado destino no esta permitido lanza
     * {@link TransicionNoPermitidaException} (HTTP 409). Al llegar a PAGADA
     * registra fechaConfirmacion. Crea un {@link OrdenEvento} por transicion.
     *
     * @param id identificador de la orden
     * @param azureSub sub del usuario autenticado (dueño y actor del evento)
     * @param nuevoEstado estado destino
     * @return la orden actualizada, o vacio si no existe/no pertenece al sub
     */
    public Optional<Orden> cambiarEstado(UUID id, String azureSub, EstadoOrden nuevoEstado) {
        if (nuevoEstado == null) {
            throw new EstadoInvalidoException("null");
        }
        Optional<Orden> posible = ordenRepository.findByIdAndUsuarioSub(id, azureSub);
        if (posible.isEmpty()) {
            return Optional.empty();
        }
        Orden orden = posible.get();
        EstadoOrden actual = orden.getEstado();

        Set<EstadoOrden> permitidos = TRANSICIONES_PERMITIDAS.getOrDefault(actual, EnumSet.noneOf(EstadoOrden.class));
        if (!permitidos.contains(nuevoEstado)) {
            throw new TransicionNoPermitidaException(actual, nuevoEstado);
        }

        orden.setEstado(nuevoEstado);
        if (nuevoEstado == EstadoOrden.PAGADA) {
            orden.setFechaConfirmacion(LocalDateTime.now());
        }
        ordenRepository.save(orden);
        ordenEventoRepository.save(new OrdenEvento(orden, actual, nuevoEstado, azureSub));
        integrarTrasCambioEstado(orden, nuevoEstado);
        return Optional.of(orden);
    }

    /**
     * Cancela una orden del usuario: solo se permite desde estados no
     * finales (a diferencia del mapa estricto de {@link #cambiarEstado},
     * cancelar se permite desde cualquier estado no final). 404 si no
     * existe o no pertenece al sub; 409 si esta en un estado final
     * (ENTREGADA, CANCELADA o FALLO_PAGO).
     *
     * @param id identificador de la orden
     * @param azureSub sub del usuario autenticado (dueño)
     * @return la orden cancelada, o vacio si no existe/no pertenece al sub
     */
    public Optional<Orden> cancelar(UUID id, String azureSub) {
        Optional<Orden> posible = ordenRepository.findByIdAndUsuarioSub(id, azureSub);
        if (posible.isEmpty()) {
            return Optional.empty();
        }
        Orden orden = posible.get();
        EstadoOrden actual = orden.getEstado();

        if (EstadoOrden.estadosFinales().contains(actual)) {
            throw new TransicionNoPermitidaException(actual, EstadoOrden.CANCELADA);
        }

        orden.setEstado(EstadoOrden.CANCELADA);
        ordenRepository.save(orden);
        ordenEventoRepository.save(new OrdenEvento(orden, actual, EstadoOrden.CANCELADA, azureSub));
        integrarTrasCancelacion(orden);
        return Optional.of(orden);
    }

    /**
     * Integraciones de creacion (fase B), todas best-effort:
     * reserva de stock por item, evento ORDEN_CONFIRMADA a notificaciones
     * (con Idempotency-Key) y vaciado del carrito tras el checkout.
     * Cualquier fallo se registra y NO interrumpe la creacion de la orden.
     */
    private void integrarTrasCreacion(Orden orden, List<OrdenItem> items, String azureSub) {
        try {
            for (OrdenItem item : items) {
                inventarioClient.reservar(item.getSku(), item.getCantidad(), String.valueOf(orden.getId()));
            }
            notificacionesClient.publicar(
                    "orden-" + orden.getId() + "-confirmada",
                    eventoOrden("Orden confirmada",
                            "Tu orden " + orden.getId() + " fue confirmada con total " + orden.getTotal() + ".",
                            orden));
            carritoClient.vaciar(azureSub);
        } catch (RuntimeException ex) {
            log.error("Integraciones tras crear la orden fallaron (flujo principal continuado): {}",
                    ex.getMessage());
        }
    }

    /**
     * Integraciones de cambio de estado (fase B), best-effort:
     * al PAGAR confirma las reservas de stock y publica orden.confirmada en
     * RabbitMQ; al CANCELAR publica orden.cancelada; al PREPARAR/ENVIAR crea
     * el envio en calisat-ms-envios con el snapshot de direccion de la orden.
     */
    private void integrarTrasCambioEstado(Orden orden, EstadoOrden nuevoEstado) {
        try {
            if (nuevoEstado == EstadoOrden.PAGADA) {
                for (OrdenItem item : ordenItemRepository.findByOrdenId(orden.getId())) {
                    inventarioClient.confirmar(item.getSku(), item.getCantidad(), String.valueOf(orden.getId()));
                }
                publicarEnRabbit(RabbitConfig.ROUTING_KEY_ORDEN_CONFIRMADA,
                        mensajeDe(orden, "ORDEN_CONFIRMADA"));
            }
            if (nuevoEstado == EstadoOrden.CANCELADA) {
                publicarEnRabbit(RabbitConfig.ROUTING_KEY_ORDEN_CANCELADA,
                        mensajeDe(orden, "ORDEN_CANCELADA"));
            }
            if (nuevoEstado == EstadoOrden.EN_PREPARACION || nuevoEstado == EstadoOrden.ENVIADA) {
                enviosClient.crear(new EnvioSolicitudDto(
                        orden.getId(),
                        orden.getUsuarioSub(),
                        orden.getDireccionCalle(),
                        orden.getDireccionCiudad(),
                        orden.getDireccionPais(),
                        orden.getDireccionCodigoPostal(),
                        null));
            }
        } catch (RuntimeException ex) {
            log.error("Integraciones de cambio de estado fallaron (flujo principal continuado): {}",
                    ex.getMessage());
        }
    }

    /**
     * Integraciones de cancelacion (fase B), best-effort: libera las
     * reservas de stock en inventario y publica ORDEN_CANCELADA (HTTP) y
     * orden.cancelada en RabbitMQ.
     */
    private void integrarTrasCancelacion(Orden orden) {
        try {
            for (OrdenItem item : ordenItemRepository.findByOrdenId(orden.getId())) {
                inventarioClient.liberar(item.getSku(), item.getCantidad(), String.valueOf(orden.getId()));
            }
            notificacionesClient.publicar(
                    "orden-" + orden.getId() + "-cancelada",
                    eventoOrden("Orden cancelada",
                            "Tu orden " + orden.getId() + " fue cancelada.",
                            orden));
            publicarEnRabbit(RabbitConfig.ROUTING_KEY_ORDEN_CANCELADA,
                    mensajeDe(orden, "ORDEN_CANCELADA"));
        } catch (RuntimeException ex) {
            log.error("Integraciones de cancelacion fallaron (flujo principal continuado): {}",
                    ex.getMessage());
        }
    }

    /**
     * Publica un evento de orden en calisat.exchange (mejor esfuerzo):
     * cualquier fallo del broker se registra y NO interrumpe el flujo.
     */
    private void publicarEnRabbit(String routingKey, OrdenMensaje mensaje) {
        try {
            rabbitTemplate.convertAndSend(routingKey, mensaje);
        } catch (RuntimeException ex) {
            log.error("No se pudo publicar '{}' en RabbitMQ (flujo principal continuado): {}",
                    routingKey, ex.getMessage());
        }
    }

    /** Construye el mensaje JSON de orden hacia calisat-ms-notificaciones. */
    private OrdenMensaje mensajeDe(Orden orden, String evento) {
        return new OrdenMensaje(
                String.valueOf(orden.getId()),
                orden.getUsuarioSub(),
                evento,
                orden.getEstado() != null ? orden.getEstado().name() : null,
                orden.getTotal() != null ? orden.getTotal().toPlainString() : null);
    }

    /** Construye el payload de evento hacia calisat-ms-notificaciones. */
    private NotificacionEventoDto eventoOrden(String asunto, String cuerpoTexto, Orden orden) {
        String payload = "{\"ordenId\":\"" + orden.getId()
                + "\",\"estado\":\"" + orden.getEstado()
                + "\",\"total\":\"" + orden.getTotal() + "\"}";
        return new NotificacionEventoDto(
                "EMAIL",
                "SISTEMA",
                asunto,
                cuerpoTexto,
                null,
                orden.getUsuarioSub(),
                null,
                null,
                null,
                payload,
                "calisat-ms-orden",
                "orden-" + orden.getId());
    }
}
