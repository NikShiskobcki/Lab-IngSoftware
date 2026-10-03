package com.laboratorio.turnos.facturacion.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

// mapeo parcial de personal (solo lectura; la tabla la crea turno-subscriber)
@Entity
@Table(name = "personal")
public class Personal {

    @Id
    private Integer id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "especialidad", nullable = false)
    private String especialidad;

    @Column(name = "costo_consulta", nullable = false)
    private BigDecimal costoConsulta;

    protected Personal() {}

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public String getEspecialidad() { return especialidad; }
    public BigDecimal getCostoConsulta() { return costoConsulta; }
}
