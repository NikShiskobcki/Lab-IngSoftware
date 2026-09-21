package com.laboratorio.turnos.api.personal;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name="personal")
public class Personal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_establecimiento", nullable = false)
    private Long idEstablecimiento;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "especialidad", nullable = false)
    private String especialidad;

    @Column(name = "costo_consulta",nullable = false)
    private BigDecimal costoConsulta;

    @Column(name = "duracion_estandar_minutos", nullable = false)
    private Integer duracionEstandarMinutos;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoPersonal estado = EstadoPersonal.ACTIVO;

    public Personal(Long id, Long idEstablecimiento, String nombre, String especialidad, BigDecimal costoConsulta, Integer duracionEstandarMinutos, EstadoPersonal estado) {
        this.id = id;
        this.idEstablecimiento = idEstablecimiento;
        this.nombre = nombre;
        this.especialidad = especialidad;
        this.costoConsulta = costoConsulta;
        this.duracionEstandarMinutos = duracionEstandarMinutos;
        this.estado = estado;
    }

    public Personal() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getIdEstablecimiento() {
        return idEstablecimiento;
    }

    public void setIdEstablecimiento(Long idEstablecimiento) {
        this.idEstablecimiento = idEstablecimiento;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEspecialidad() {
        return especialidad;
    }

    public void setEspecialidad(String especialidad) {
        this.especialidad = especialidad;
    }

    public BigDecimal getCostoConsulta() {
        return costoConsulta;
    }

    public void setCostoConsulta(BigDecimal costoConsulta) {
        this.costoConsulta = costoConsulta;
    }

    public Integer getDuracionEstandarMinutos() {
        return duracionEstandarMinutos;
    }

    public void setDuracionEstandarMinutos(Integer duracionEstandarMinutos) {
        this.duracionEstandarMinutos = duracionEstandarMinutos;
    }

    public EstadoPersonal getEstado() {
        return estado;
    }

    public void setEstado(EstadoPersonal estado) {
        this.estado = estado;
    }
}
