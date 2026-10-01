package com.laboratorio.turnos.api.personal.dto;

import java.time.LocalTime;

public class TurnoAgendaResponse {

    private Integer idReserva;
    private LocalTime hora;
    private Integer duracionMinutos;
    private String estado;
    private String emailSolicitante;
    private String telefonoSolicitante;

    public TurnoAgendaResponse(
            Integer idReserva,
            LocalTime hora,
            Integer duracionMinutos,
            String estado,
            String emailSolicitante,
            String telefonoSolicitante
    ) {
        this.idReserva = idReserva;
        this.hora = hora;
        this.duracionMinutos = duracionMinutos;
        this.estado = estado;
        this.emailSolicitante = emailSolicitante;
        this.telefonoSolicitante = telefonoSolicitante;
    }

    public Integer getIdReserva() {
        return idReserva;
    }

    public LocalTime getHora() {
        return hora;
    }

    public Integer getDuracionMinutos() {
        return duracionMinutos;
    }

    public String getEstado() {
        return estado;
    }

    public String getEmailSolicitante() {
        return emailSolicitante;
    }

    public String getTelefonoSolicitante() {
        return telefonoSolicitante;
    }
}