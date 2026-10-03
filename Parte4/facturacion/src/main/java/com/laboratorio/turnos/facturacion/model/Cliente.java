package com.laboratorio.turnos.facturacion.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

// cliente identificado de forma unica por email
@Entity
@Table(name = "clientes",
        uniqueConstraints = @UniqueConstraint(name = "uq_cliente_email", columnNames = "email"))
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "telefono", length = 50)
    private String telefono;

    @Column(name = "fecha_alta", nullable = false)
    private LocalDateTime fechaAlta;

    public Cliente() {}

    public Cliente(String email, String telefono, LocalDateTime fechaAlta) {
        this.email = email;
        this.telefono = telefono;
        this.fechaAlta = fechaAlta;
    }

    public Integer getId() { return id; }
    public String getEmail() { return email; }
    public String getTelefono() { return telefono; }
    public LocalDateTime getFechaAlta() { return fechaAlta; }
}
