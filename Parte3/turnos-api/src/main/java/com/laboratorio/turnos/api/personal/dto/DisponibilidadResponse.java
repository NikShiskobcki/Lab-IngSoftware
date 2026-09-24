package com.laboratorio.turnos.api.personal.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class DisponibilidadResponse {

    private Long idPersonal;
    private String nombrePersonal;
    private LocalDate fecha;
    private Integer duracionTurnoMinutos;
    private List<LocalTime> horariosDisponibles;

    public DisponibilidadResponse(
            Long idPersonal,
            String nombrePersonal,
            LocalDate fecha,
            Integer duracionTurnoMinutos,
            List<LocalTime> horariosDisponibles
    ) {
        this.idPersonal = idPersonal;
        this.nombrePersonal = nombrePersonal;
        this.fecha = fecha;
        this.duracionTurnoMinutos = duracionTurnoMinutos;
        this.horariosDisponibles = horariosDisponibles;
    }

    public Long getIdPersonal() {
        return idPersonal;
    }

    public String getNombrePersonal() {
        return nombrePersonal;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public Integer getDuracionTurnoMinutos() {
        return duracionTurnoMinutos;
    }

    public List<LocalTime> getHorariosDisponibles() {
        return horariosDisponibles;
    }
}