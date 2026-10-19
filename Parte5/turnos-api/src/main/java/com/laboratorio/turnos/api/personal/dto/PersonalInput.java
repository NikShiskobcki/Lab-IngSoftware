package com.laboratorio.turnos.api.personal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.laboratorio.turnos.api.personal.EstadoPersonal;
import java.math.BigDecimal;

public class PersonalInput {
    @NotNull(message="idEstablecimiento es obligatorio")
    private Long idEstablecimiento;

    @NotBlank(message="nombre es obligatorio")
    private String nombre;

    @NotBlank(message="especialidad es obligatoria")
    private String especialidad;

    @NotNull(message="costoConsulta es obligatorio")
    @DecimalMin(value="0.0", inclusive = false, message = "costoConsulta debe ser mayor que 0")
    private BigDecimal costoConsulta;

    @NotNull(message="duracionEstandarMinutos es obligatorio")
    @Positive(message = "duracionEstandarMinutos debe ser mayor que 0")
    private Integer duracionEstandarMinutos;

    private EstadoPersonal estado;

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
