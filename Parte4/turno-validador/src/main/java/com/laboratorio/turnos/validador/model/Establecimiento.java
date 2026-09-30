package com.laboratorio.turnos.validador.model;

import jakarta.persistence.*;

// entidad mapeada a establecimientos
@Entity
@Table(name = "establecimientos")
public class Establecimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nombre_comercial", nullable = false)
    private String nombreComercial;

    public Establecimiento() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNombreComercial() { return nombreComercial; }
    public void setNombreComercial(String nombreComercial) { this.nombreComercial = nombreComercial; }
}
