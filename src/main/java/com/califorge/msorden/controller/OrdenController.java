package com.califorge.msorden.controller;

import com.califorge.msorden.dto.OrdenCreateRequest;
import com.califorge.msorden.dto.OrdenEstadoRequest;
import com.califorge.msorden.dto.OrdenResponse;
import com.califorge.msorden.model.EstadoOrden;
import com.califorge.msorden.model.Orden;
import com.califorge.msorden.service.OrdenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ordenes")
@Tag(name = "Ordenes de compra", description = "Gestion de ordenes del usuario autenticado (JWT). Sin RBAC: todo usuario autenticado accede solo a sus propias ordenes.")
public class OrdenController {

    private final OrdenService ordenService;

    public OrdenController(OrdenService ordenService) {
        this.ordenService = ordenService;
    }

    /**
     * Crea una orden para el usuario autenticado con los items (precios
     * snapshot) y la direccion del request.
     *
     * <p>Idempotencia: acepta la clave en el header {@code Idempotency-Key}
     * (prioridad) o en el body; si la clave ya existe devuelve la orden
     * previa con HTTP 200; si se crea nueva, HTTP 201 con Location.</p>
     *
     * @param idempotencyKeyHeader clave de idempotencia opcional en el header
     * @param jwt token JWT del usuario autenticado (claim sub)
     * @param request items y datos de la orden
     * @return 201 + Location si se creo, 200 si se reutilizo por idempotencia
     */
    @Operation(summary = "Crear orden", description = "Crea una orden del usuario autenticado con sus items (snapshot de precios). Devuelve 201 con Location; si la clave de idempotencia (header Idempotency-Key con prioridad, o body) ya existe, devuelve 200 con la orden existente. Requiere JWT.")
    @PostMapping
    public ResponseEntity<OrdenResponse> crear(
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Parameter(description = "Clave de idempotencia opcional. Si tambien viene en el body, el header tiene prioridad.", example = "orden-2026-001")
            String idempotencyKeyHeader,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OrdenCreateRequest request) {
        OrdenService.Creacion creacion = ordenService.crear(jwt.getSubject(), request, idempotencyKeyHeader);
        OrdenResponse body = OrdenResponse.desde(
                creacion.orden(), ordenService.itemsDe(creacion.orden().getId()));
        if (creacion.reutilizada()) {
            return ResponseEntity.ok(body);
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(body.id())
                .toUri();
        return ResponseEntity.created(location).body(body);
    }

    /**
     * Lista las ordenes propias del usuario autenticado, paginadas y
     * ordenadas de mas reciente a mas antigua.
     *
     * @param jwt token JWT del usuario autenticado (claim sub)
     * @param pageable paginacion (tamanio por defecto 20)
     * @return 200 con la pagina de ordenes del usuario
     */
    @Operation(summary = "Listar mis ordenes", description = "Devuelve paginadas solo las ordenes del usuario autenticado (de mas reciente a mas antigua). Requiere JWT.")
    @GetMapping
    public ResponseEntity<Page<OrdenResponse>> listarPropias(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<OrdenResponse> ordenes = ordenService.listarPropias(jwt.getSubject(), pageable)
                .map(orden -> OrdenResponse.desde(orden, ordenService.itemsDe(orden.getId())));
        return ResponseEntity.ok(ordenes);
    }

    /**
     * Consulta una orden por id. 404 si no existe o si no pertenece al
     * usuario autenticado (aislamiento entre usuarios, sin RBAC).
     *
     * @param id identificador de la orden
     * @param jwt token JWT del usuario autenticado (claim sub)
     * @return 200 con la orden si es del dueño; 404 en otro caso
     */
    @Operation(summary = "Consultar orden por id", description = "Detalle de una orden del usuario autenticado. 404 si no existe o si pertenece a otro usuario. Requiere JWT.")
    @GetMapping("/{id}")
    public ResponseEntity<OrdenResponse> buscarPorId(
            @Parameter(name = "id", description = "Identificador (UUID) de la orden.", required = true)
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ordenService.buscarPorId(id, jwt.getSubject())
                .map(orden -> OrdenResponse.desde(orden, ordenService.itemsDe(orden.getId())))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Cambia el estado de una orden validando la maquina de estados.
     *
     * @param id identificador de la orden
     * @param jwt token JWT del usuario autenticado (claim sub, dueño)
     * @param request estado destino
     * @return 200 con la orden actualizada; 404 si no existe/no es del
     *         dueño; 409 si la transicion no esta permitida; 400 si el
     *         estado no es valido
     */
    @Operation(summary = "Cambiar estado de una orden", description = "Aplica una transicion de la maquina de estados (PENDIENTE->PAGADA|CANCELADA|FALLO_PAGO, PAGADA->EN_PREPARACION|CANCELADA, EN_PREPARACION->ENVIADA, ENVIADA->ENTREGADA). 200 OK, 404 si no existe o no es del dueño, 409 si la transicion es ilegal, 400 si el estado es desconocido. Requiere JWT.")
    @PutMapping("/{id}/estado")
    public ResponseEntity<OrdenResponse> cambiarEstado(
            @Parameter(name = "id", description = "Identificador (UUID) de la orden.", required = true)
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OrdenEstadoRequest request) {
        EstadoOrden nuevoEstado = EstadoOrden.desdeTexto(request.estado());
        return ordenService.cambiarEstado(id, jwt.getSubject(), nuevoEstado)
                .map(orden -> OrdenResponse.desde(orden, ordenService.itemsDe(orden.getId())))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Cancela una orden del usuario mientras este en un estado no final.
     *
     * @param id identificador de la orden
     * @param jwt token JWT del usuario autenticado (claim sub, dueño)
     * @return 200 con la orden cancelada; 404 si no existe/no es del
     *         dueño; 409 si ya esta en un estado final
     */
    @Operation(summary = "Cancelar orden", description = "Cancela una orden del usuario autenticado si no esta en un estado final (ENTREGADA, CANCELADA o FALLO_PAGO). 200 OK, 404 si no existe o no es del dueño, 409 si esta en estado final. Requiere JWT.")
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<OrdenResponse> cancelar(
            @Parameter(name = "id", description = "Identificador (UUID) de la orden.", required = true)
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ordenService.cancelar(id, jwt.getSubject())
                .map(orden -> OrdenResponse.desde(orden, ordenService.itemsDe(orden.getId())))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
