package com.laboratorio.turnos.facturacion.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// una factura por cliente y mes
@Entity
@Table(name = "facturas",
        uniqueConstraints = @UniqueConstraint(name = "uq_factura_cliente_periodo",
                columnNames = {"id_cliente", "anio", "mes"}))
public class Factura {

    public static final String ESTADO_ABIERTA = "ABIERTA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cliente", nullable = false, foreignKey = @ForeignKey(name = "fk_factura_cliente"))
    private Cliente cliente;

    @Column(name = "anio", nullable = false, columnDefinition = "SMALLINT")
    private Integer anio;

    @Column(name = "mes", nullable = false, columnDefinition = "TINYINT")
    private Integer mes;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    @ColumnDefault("0")
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "estado", nullable = false, length = 20)
    @ColumnDefault("'ABIERTA'")
    private String estado = ESTADO_ABIERTA;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    public Factura() {}

    public Factura(Cliente cliente, int anio, int mes, LocalDateTime fechaCreacion) {
        this.cliente = cliente;
        this.anio = anio;
        this.mes = mes;
        this.fechaCreacion = fechaCreacion;
    }

    public Integer getId() { return id; }
    public Cliente getCliente() { return cliente; }
    public Integer getAnio() { return anio; }
    public Integer getMes() { return mes; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public String getEstado() { return estado; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
}
