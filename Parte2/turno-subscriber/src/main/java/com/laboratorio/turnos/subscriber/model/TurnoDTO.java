package com.laboratorio.turnos.subscriber.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Modelo de datos del turno recibido desde MQTT.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TurnoDTO {

    @JsonProperty("id")
    private Integer id;

    @JsonProperty("email_cliente")
    private String emailCliente;

    @JsonProperty("telefono_cliente")
    private String telefonoCliente;

    @JsonProperty("idPersonal")
    private Integer idPersonal;

    @JsonProperty("fecha")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate fecha;

    @JsonProperty("hora")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    private LocalTime hora;

    public TurnoDTO() {
    }

    public TurnoDTO(Integer id, String emailCliente, String telefonoCliente, Integer idPersonal, LocalDate fecha, LocalTime hora) {
        this.id = id;
        this.emailCliente = emailCliente;
        this.telefonoCliente = telefonoCliente;
        this.idPersonal = idPersonal;
        this.fecha = fecha;
        this.hora = hora;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getEmailCliente() {
        return emailCliente;
    }

    public void setEmailCliente(String emailCliente) {
        this.emailCliente = emailCliente;
    }

    public String getTelefonoCliente() {
        return telefonoCliente;
    }

    public void setTelefonoCliente(String telefonoCliente) {
        this.telefonoCliente = telefonoCliente;
    }

    public Integer getIdPersonal() {
        return idPersonal;
    }

    public void setIdPersonal(Integer idPersonal) {
        this.idPersonal = idPersonal;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public LocalTime getHora() {
        return hora;
    }

    public void setHora(LocalTime hora) {
        this.hora = hora;
    }

    @Override
    public String toString() {
        return "TurnoDTO{" +
                "id=" + id +
                ", emailCliente='" + emailCliente + '\'' +
                ", telefonoCliente='" + telefonoCliente + '\'' +
                ", idPersonal=" + idPersonal +
                ", fecha=" + fecha +
                ", hora=" + hora +
                '}';
    }
}
