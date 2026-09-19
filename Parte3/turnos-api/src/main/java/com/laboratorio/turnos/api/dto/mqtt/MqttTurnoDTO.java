package com.laboratorio.turnos.api.dto.mqtt;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalTime;

// dto del turno para el payload mqtt
public class MqttTurnoDTO {

    @JsonProperty("idPersonal")
    private Integer idPersonal;

    @JsonProperty("email_cliente")
    private String emailCliente;

    @JsonProperty("telefono_cliente")
    private String telefonoCliente;

    @JsonProperty("fecha")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate fecha;

    @JsonProperty("hora")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    private LocalTime hora;

    public MqttTurnoDTO() {}

    public MqttTurnoDTO(Integer idPersonal, String emailCliente, String telefonoCliente, LocalDate fecha, LocalTime hora) {
        this.idPersonal = idPersonal;
        this.emailCliente = emailCliente;
        this.telefonoCliente = telefonoCliente;
        this.fecha = fecha;
        this.hora = hora;
    }

    public Integer getIdPersonal() { return idPersonal; }
    public void setIdPersonal(Integer idPersonal) { this.idPersonal = idPersonal; }

    public String getEmailCliente() { return emailCliente; }
    public void setEmailCliente(String emailCliente) { this.emailCliente = emailCliente; }

    public String getTelefonoCliente() { return telefonoCliente; }
    public void setTelefonoCliente(String telefonoCliente) { this.telefonoCliente = telefonoCliente; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public LocalTime getHora() { return hora; }
    public void setHora(LocalTime hora) { this.hora = hora; }
}
