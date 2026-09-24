package com.califorge.msorden.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Entity
@Table(name = "orden_evento")
public class OrdenEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_id", nullable = false)
    private Orden orden;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 30)
    private EstadoOrden estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 30)
    private EstadoOrden estadoNuevo;

    @Size(max = 100, message = "actor no puede superar 100 caracteres")
    @Column(name = "actor", nullable = false, length = 100)
    private String actor;

    @Column(name = "fecha_evento", nullable = false)
    private LocalDateTime fechaEvento;

    @PrePersist
    protected void onCreate() {
        fechaEvento = LocalDateTime.now();
    }

    public OrdenEvento() {}

    /** Evento inicial (estadoAnterior puede ser null al crearse la orden). */
    public OrdenEvento(Orden orden, EstadoOrden estadoAnterior, EstadoOrden estadoNuevo, String actor) {
        this.orden = orden;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.actor = actor;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Orden getOrden() { return orden; }
    public void setOrden(Orden orden) { this.orden = orden; }

    public EstadoOrden getEstadoAnterior() { return estadoAnterior; }
    public void setEstadoAnterior(EstadoOrden estadoAnterior) { this.estadoAnterior = estadoAnterior; }

    public EstadoOrden getEstadoNuevo() { return estadoNuevo; }
    public void setEstadoNuevo(EstadoOrden estadoNuevo) { this.estadoNuevo = estadoNuevo; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public LocalDateTime getFechaEvento() { return fechaEvento; }
    public void setFechaEvento(LocalDateTime fechaEvento) { this.fechaEvento = fechaEvento; }
}
