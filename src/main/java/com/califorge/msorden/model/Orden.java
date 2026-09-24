package com.califorge.msorden.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "orden", indexes = {
        @Index(name = "idx_orden_usuario_sub", columnList = "usuario_sub")
})
public class Orden {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank(message = "usuarioSub es obligatorio")
    @Size(max = 100, message = "usuarioSub no puede superar 100 caracteres")
    @Column(name = "usuario_sub", nullable = false, length = 100)
    private String usuarioSub;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    private EstadoOrden estado = EstadoOrden.PENDIENTE;

    @Column(name = "subtotal", precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "total", precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "idempotency_key", unique = true, nullable = true, length = 100)
    private String idempotencyKey;

    @Size(max = 200, message = "direccionCalle no puede superar 200 caracteres")
    @Column(name = "direccion_calle", length = 200)
    private String direccionCalle;

    @Size(max = 100, message = "direccionCiudad no puede superar 100 caracteres")
    @Column(name = "direccion_ciudad", length = 100)
    private String direccionCiudad;

    @Size(max = 100, message = "direccionPais no puede superar 100 caracteres")
    @Column(name = "direccion_pais", length = 100)
    private String direccionPais;

    @Size(max = 20, message = "direccionCodigoPostal no puede superar 20 caracteres")
    @Column(name = "direccion_codigo_postal", length = 20)
    private String direccionCodigoPostal;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @Column(name = "fecha_confirmacion")
    private LocalDateTime fechaConfirmacion;

    @PrePersist
    protected void onCreate() {
        LocalDateTime ahora = LocalDateTime.now();
        fechaCreacion = ahora;
        fechaActualizacion = ahora;
    }

    @PreUpdate
    protected void onUpdate() {
        fechaActualizacion = LocalDateTime.now();
    }

    public Orden() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getUsuarioSub() { return usuarioSub; }
    public void setUsuarioSub(String usuarioSub) { this.usuarioSub = usuarioSub; }

    public EstadoOrden getEstado() { return estado; }
    public void setEstado(EstadoOrden estado) { this.estado = estado; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getDireccionCalle() { return direccionCalle; }
    public void setDireccionCalle(String direccionCalle) { this.direccionCalle = direccionCalle; }

    public String getDireccionCiudad() { return direccionCiudad; }
    public void setDireccionCiudad(String direccionCiudad) { this.direccionCiudad = direccionCiudad; }

    public String getDireccionPais() { return direccionPais; }
    public void setDireccionPais(String direccionPais) { this.direccionPais = direccionPais; }

    public String getDireccionCodigoPostal() { return direccionCodigoPostal; }
    public void setDireccionCodigoPostal(String direccionCodigoPostal) { this.direccionCodigoPostal = direccionCodigoPostal; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }

    public LocalDateTime getFechaConfirmacion() { return fechaConfirmacion; }
    public void setFechaConfirmacion(LocalDateTime fechaConfirmacion) { this.fechaConfirmacion = fechaConfirmacion; }
}
