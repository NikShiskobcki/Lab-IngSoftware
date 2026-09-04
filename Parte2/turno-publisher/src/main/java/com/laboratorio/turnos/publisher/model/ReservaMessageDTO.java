package com.laboratorio.turnos.publisher.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Representa el mensaje de evento JSON publicado hacia el broker MQTT.
 */
public class ReservaMessageDTO {

    @JsonProperty("status")
    private String status;

    @JsonProperty("fechaHora")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime fechaHora;

    @JsonProperty("turno")
    private TurnoDTO turno;

    public ReservaMessageDTO() {
    }

    public ReservaMessageDTO(String status, LocalDateTime fechaHora, TurnoDTO turno) {
        this.status = status;
        this.fechaHora = fechaHora;
        this.turno = turno;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public TurnoDTO getTurno() {
        return turno;
    }

    public void setTurno(TurnoDTO turno) {
        this.turno = turno;
    }

    @Override
    public String toString() {
        return "ReservaMessageDTO{" +
                "status='" + status + '\'' +
                ", fechaHora=" + fechaHora +
                ", turno=" + turno +
                '}';
    }
}
