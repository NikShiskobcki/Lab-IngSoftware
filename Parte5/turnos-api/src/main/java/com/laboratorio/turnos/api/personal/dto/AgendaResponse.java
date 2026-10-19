package com.laboratorio.turnos.api.personal.dto;

import java.time.LocalDate;
import java.util.List;

public class AgendaResponse {

    private Long idPersonal;
    private String nombrePersonal;
    private LocalDate fecha;
    private List<TurnoAgendaResponse> turnos;

    public AgendaResponse(
            Long idPersonal,
            String nombrePersonal,
            LocalDate fecha,
            List<TurnoAgendaResponse> turnos
    ) {
        this.idPersonal = idPersonal;
        this.nombrePersonal = nombrePersonal;
        this.fecha = fecha;
        this.turnos = turnos;
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

    public List<TurnoAgendaResponse> getTurnos() {
        return turnos;
    }
}
