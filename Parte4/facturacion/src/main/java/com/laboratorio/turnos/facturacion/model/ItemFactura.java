package com.laboratorio.turnos.facturacion.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;

// un item por turno facturado (uq_item_turno evita facturar dos veces el mismo turno)
@Entity
@Table(name = "items_factura",
        uniqueConstraints = @UniqueConstraint(name = "uq_item_turno", columnNames = "id_turno"))
public class ItemFactura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_factura", nullable = false, foreignKey = @ForeignKey(name = "fk_item_factura"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Factura factura;

    // referencia a reservas_turnos (tabla de otro servicio, la crea turno-subscriber).
    // sin constraint fisica: hibernate no necesita que esa tabla exista para crear items_factura.
    // uq_item_turno sigue impidiendo facturar dos veces el mismo turno.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_turno", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Reserva turno;

    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    @Column(name = "monto", nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    public ItemFactura() {}

    public ItemFactura(Factura factura, Reserva turno, String descripcion, BigDecimal monto) {
        this.factura = factura;
        this.turno = turno;
        this.descripcion = descripcion;
        this.monto = monto;
    }

    public Integer getId() { return id; }
    public Factura getFactura() { return factura; }
    public Reserva getTurno() { return turno; }
    public String getDescripcion() { return descripcion; }
    public BigDecimal getMonto() { return monto; }
}
