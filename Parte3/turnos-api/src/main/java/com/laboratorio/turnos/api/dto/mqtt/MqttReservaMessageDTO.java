package com.laboratorio.turnos.api.dto.mqtt;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

// dto del mensaje general publicado hacia el broker mqtt
public class MqttReservaMessageDTO {

    @JsonProperty("status")
    private String status;

    @JsonProperty("fechaHora")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime fechaHora;

    @JsonProperty("turno")
    private MqttTurnoDTO turno;

    public MqttReservaMessageDTO() {}

    public MqttReservaMessageDTO(String status, LocalDateTime fechaHora, MqttTurnoDTO turno) {
        this.status = status;
        this.fechaHora = fechaHora;
        this.turno = turno;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }

    public MqttTurnoDTO getTurno() { return turno; }
    public void setTurno(MqttTurnoDTO turno) { this.turno = turno; }
}
