package com.califorge.msorden.service;

import com.califorge.msorden.dto.OrdenCreateRequest;
import com.califorge.msorden.dto.OrdenItemRequest;
import com.califorge.msorden.exception.TransicionNoPermitidaException;
import com.califorge.msorden.model.EstadoOrden;
import com.califorge.msorden.model.Orden;
import com.califorge.msorden.model.OrdenEvento;
import com.califorge.msorden.model.OrdenItem;
import com.califorge.msorden.repository.OrdenEventoRepository;
import com.califorge.msorden.repository.OrdenItemRepository;
import com.califorge.msorden.repository.OrdenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrdenServiceTest {

    @Mock
    private OrdenRepository ordenRepository;

    @Mock
    private OrdenItemRepository ordenItemRepository;

    @Mock
    private OrdenEventoRepository ordenEventoRepository;

    @InjectMocks
    private OrdenService ordenService;

    private static final String SUB = "usuario-azure-001";

    @Test
    void crear_calculaTotalesGuardaItemsYEventoInicial() {
        when(ordenRepository.findByIdempotencyKey("clave-1")).thenReturn(Optional.empty());
        when(ordenRepository.save(any(Orden.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ordenItemRepository.save(any(OrdenItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ordenEventoRepository.save(any(OrdenEvento.class))).thenAnswer(inv -> inv.getArgument(0));

        OrdenCreateRequest request = new OrdenCreateRequest(
                List.of(
                        new OrdenItemRequest("SKU-1", "Anillas", 2, new BigDecimal("10.00")),
                        new OrdenItemRequest("SKU-2", "Cuerda", 1, new BigDecimal("5.50"))),
                "Calle Mayor 1", "Madrid", "Espana", "28001",
                "clave-1");

        OrdenService.Creacion creacion = ordenService.crear(SUB, request, null);

        assertFalse(creacion.reutilizada());
        Orden orden = creacion.orden();
        assertEquals(SUB, orden.getUsuarioSub());
        assertEquals(EstadoOrden.PENDIENTE, orden.getEstado());
        assertEquals("clave-1", orden.getIdempotencyKey());
        assertEquals("Calle Mayor 1", orden.getDireccionCalle());
        assertEquals(0, new BigDecimal("25.50").compareTo(orden.getSubtotal()));
        assertEquals(0, new BigDecimal("25.50").compareTo(orden.getTotal()));

        ArgumentCaptor<OrdenItem> itemCaptor = ArgumentCaptor.forClass(OrdenItem.class);
        verify(ordenItemRepository, times(2)).save(itemCaptor.capture());
        List<OrdenItem> items = itemCaptor.getAllValues();
        assertEquals("SKU-1", items.get(0).getSku());
        assertEquals(0, new BigDecimal("20.00").compareTo(items.get(0).getSubtotal()));
        assertEquals("SKU-2", items.get(1).getSku());
        assertEquals(0, new BigDecimal("5.50").compareTo(items.get(1).getSubtotal()));

        ArgumentCaptor<OrdenEvento> eventoCaptor = ArgumentCaptor.forClass(OrdenEvento.class);
        verify(ordenEventoRepository).save(eventoCaptor.capture());
        OrdenEvento evento = eventoCaptor.getValue();
        assertNull(evento.getEstadoAnterior());
        assertEquals(EstadoOrden.PENDIENTE, evento.getEstadoNuevo());
        assertEquals(SUB, evento.getActor());
    }

    @Test
    void crear_conIdempotencyKeyExistente_reutilizaOrdenPrevia() {
        Orden previa = orden(EstadoOrden.PENDIENTE);
        previa.setIdempotencyKey("repetida");
        when(ordenRepository.findByIdempotencyKey("repetida")).thenReturn(Optional.of(previa));

        OrdenCreateRequest request = new OrdenCreateRequest(
                List.of(new OrdenItemRequest("SKU-1", "Anillas", 1, new BigDecimal("9.99"))),
                null, null, null, null,
                "repetida");

        OrdenService.Creacion creacion = ordenService.crear(SUB, request, null);

        assertTrue(creacion.reutilizada());
        assertSame(previa, creacion.orden());
        verify(ordenRepository, never()).save(any(Orden.class));
        verify(ordenItemRepository, never()).save(any(OrdenItem.class));
        verify(ordenEventoRepository, never()).save(any(OrdenEvento.class));
    }

    @Test
    void crear_headerIdempotencyKey_tienePrioridadSobreElBody() {
        Orden previa = orden(EstadoOrden.PENDIENTE);
        when(ordenRepository.findByIdempotencyKey("header-1")).thenReturn(Optional.of(previa));

        OrdenCreateRequest request = new OrdenCreateRequest(
                List.of(new OrdenItemRequest("SKU-1", "Anillas", 1, new BigDecimal("9.99"))),
                null, null, null, null,
                "body-1");

        OrdenService.Creacion creacion = ordenService.crear(SUB, request, "header-1");

        assertTrue(creacion.reutilizada());
        verify(ordenRepository).findByIdempotencyKey("header-1");
        verify(ordenRepository, never()).findByIdempotencyKey("body-1");
    }

    @Test
    void cambiarEstado_transicionIlegal_lanza409() {
        Orden orden = orden(EstadoOrden.PENDIENTE);
        orden.setId(UUID.randomUUID());
        when(ordenRepository.findByIdAndUsuarioSub(orden.getId(), SUB)).thenReturn(Optional.of(orden));

        TransicionNoPermitidaException ex = assertThrows(
                TransicionNoPermitidaException.class,
                () -> ordenService.cambiarEstado(orden.getId(), SUB, EstadoOrden.ENVIADA));

        assertEquals("Transicion no permitida: PENDIENTE -> ENVIADA", ex.getMessage());
        verify(ordenEventoRepository, never()).save(any(OrdenEvento.class));
    }

    @Test
    void cambiarEstado_pendienteAPagada_registraFechaConfirmacionYEvento() {
        Orden orden = orden(EstadoOrden.PENDIENTE);
        orden.setId(UUID.randomUUID());
        when(ordenRepository.findByIdAndUsuarioSub(orden.getId(), SUB)).thenReturn(Optional.of(orden));
        when(ordenRepository.save(any(Orden.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ordenEventoRepository.save(any(OrdenEvento.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Orden> resultado = ordenService.cambiarEstado(orden.getId(), SUB, EstadoOrden.PAGADA);

        assertTrue(resultado.isPresent());
        assertEquals(EstadoOrden.PAGADA, orden.getEstado());
        assertNotNull(orden.getFechaConfirmacion());

        ArgumentCaptor<OrdenEvento> eventoCaptor = ArgumentCaptor.forClass(OrdenEvento.class);
        verify(ordenEventoRepository).save(eventoCaptor.capture());
        assertEquals(EstadoOrden.PENDIENTE, eventoCaptor.getValue().getEstadoAnterior());
        assertEquals(EstadoOrden.PAGADA, eventoCaptor.getValue().getEstadoNuevo());
        assertEquals(SUB, eventoCaptor.getValue().getActor());
    }

    @Test
    void cambiarEstado_ordenDeOtroUsuario_devuelveVacio404() {
        UUID id = UUID.randomUUID();
        when(ordenRepository.findByIdAndUsuarioSub(id, SUB)).thenReturn(Optional.empty());

        Optional<Orden> resultado = ordenService.cambiarEstado(id, SUB, EstadoOrden.PAGADA);

        assertFalse(resultado.isPresent());
        verify(ordenRepository, never()).save(any(Orden.class));
    }

    @Test
    void cancelar_desdeEstadoNoFinal_cancelaYRegistraEvento() {
        Orden orden = orden(EstadoOrden.EN_PREPARACION);
        orden.setId(UUID.randomUUID());
        when(ordenRepository.findByIdAndUsuarioSub(orden.getId(), SUB)).thenReturn(Optional.of(orden));
        when(ordenRepository.save(any(Orden.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ordenEventoRepository.save(any(OrdenEvento.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Orden> resultado = ordenService.cancelar(orden.getId(), SUB);

        assertTrue(resultado.isPresent());
        assertEquals(EstadoOrden.CANCELADA, orden.getEstado());

        ArgumentCaptor<OrdenEvento> eventoCaptor = ArgumentCaptor.forClass(OrdenEvento.class);
        verify(ordenEventoRepository).save(eventoCaptor.capture());
        assertEquals(EstadoOrden.EN_PREPARACION, eventoCaptor.getValue().getEstadoAnterior());
        assertEquals(EstadoOrden.CANCELADA, eventoCaptor.getValue().getEstadoNuevo());
        assertEquals(SUB, eventoCaptor.getValue().getActor());
    }

    @Test
    void cancelar_desdeEstadoFinal_lanza409() {
        Orden orden = orden(EstadoOrden.ENTREGADA);
        orden.setId(UUID.randomUUID());
        when(ordenRepository.findByIdAndUsuarioSub(orden.getId(), SUB)).thenReturn(Optional.of(orden));

        TransicionNoPermitidaException ex = assertThrows(
                TransicionNoPermitidaException.class,
                () -> ordenService.cancelar(orden.getId(), SUB));

        assertEquals("Transicion no permitida: ENTREGADA -> CANCELADA", ex.getMessage());
        verify(ordenRepository, never()).save(any(Orden.class));
    }

    @Test
    void cancelar_ordenInexistente_devuelveVacio404() {
        UUID id = UUID.randomUUID();
        when(ordenRepository.findByIdAndUsuarioSub(id, SUB)).thenReturn(Optional.empty());

        Optional<Orden> resultado = ordenService.cancelar(id, SUB);

        assertFalse(resultado.isPresent());
        verify(ordenEventoRepository, never()).save(any(OrdenEvento.class));
    }

    @Test
    void itemsDe_devuelveItemsDeLaOrden() {
        Orden orden = orden(EstadoOrden.PENDIENTE);
        OrdenItem item = new OrdenItem();
        item.setOrden(orden);
        item.setSku("SKU-1");
        when(ordenItemRepository.findByOrdenId(orden.getId())).thenReturn(List.of(item));

        List<OrdenItem> items = ordenService.itemsDe(orden.getId());

        assertEquals(1, items.size());
        assertEquals("SKU-1", items.get(0).getSku());
    }

    private Orden orden(EstadoOrden estado) {
        Orden orden = new Orden();
        orden.setId(UUID.randomUUID());
        orden.setUsuarioSub(SUB);
        orden.setEstado(estado);
        orden.setSubtotal(new BigDecimal("25.50"));
        orden.setTotal(new BigDecimal("25.50"));
        return orden;
    }
}
