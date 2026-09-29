package com.laboratorio.turnos.api.establecimiento;

import jakarta.persistence.*;
import java.time.LocalTime;

/**
 * Mapea la tabla "establecimientos" tal como la crea
 * turno-subscriber/db/DatabaseManager.java. Como el compose define
 * SPRING_JPA_HIBERNATE_DDL_AUTO=none, esta entidad NO crea ni modifica
 * el esquema: solo lee/escribe sobre la tabla ya existente.
 */
@Entity
@Table(name = "establecimientos")
public class Establecimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_comercial", nullable = false)
    private String nombreComercial;

    @Column(name = "direccion", nullable = false)
    private String direccion;

    @Column(name = "telefono", nullable = false)
    private String telefono;

    @Column(name = "correo_electronico", nullable = false)
    private String correoElectronico;

    @Column(name = "horario_apertura", nullable = false)
    private LocalTime horarioApertura;

    @Column(name = "horario_cierre", nullable = false)
    private LocalTime horarioCierre;

    public Establecimiento() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombreComercial() { return nombreComercial; }
    public void setNombreComercial(String nombreComercial) { this.nombreComercial = nombreComercial; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getCorreoElectronico() { return correoElectronico; }
    public void setCorreoElectronico(String correoElectronico) { this.correoElectronico = correoElectronico; }

    public LocalTime getHorarioApertura() { return horarioApertura; }
    public void setHorarioApertura(LocalTime horarioApertura) { this.horarioApertura = horarioApertura; }

    public LocalTime getHorarioCierre() { return horarioCierre; }
    public void setHorarioCierre(LocalTime horarioCierre) { this.horarioCierre = horarioCierre; }
}
