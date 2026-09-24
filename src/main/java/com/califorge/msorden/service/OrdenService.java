package com.califorge.msorden.service;

import com.califorge.msorden.dto.OrdenCreateRequest;
import com.califorge.msorden.dto.OrdenItemRequest;
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
 */
@Service
@Transactional
public class OrdenService {

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

    public OrdenService(OrdenRepository ordenRepository,
                        OrdenItemRepository ordenItemRepository,
                        OrdenEventoRepository ordenEventoRepository) {
        this.ordenRepository = ordenRepository;
        this.ordenItemRepository = ordenItemRepository;
        this.ordenEventoRepository = ordenEventoRepository;
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
     * @param azureSub sub del usuario autenticado (dueno de la orden)
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
     * Busca una orden por id filtrando por el dueno (usuarioSub del JWT);
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
     * @param azureSub sub del usuario autenticado (dueno y actor del evento)
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
     * @param azureSub sub del usuario autenticado (dueno)
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
        return Optional.of(orden);
    }
}
