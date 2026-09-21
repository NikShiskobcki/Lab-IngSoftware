package com.laboratorio.turnos.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

// Recibe y valida los datos que envías desde Postman en el POST (email válido, fecha, hora, etc)
// dto de entrada para solicitar una reserva
@Schema(description = "datos requeridos para generar una reserva")
public class CrearReservaDTO {

    @Schema(description = "id del profesional", example = "1")
    @NotNull(message = "el idPersonal es obligatorio")
    private Integer idPersonal;

    @Schema(description = "correo del cliente", example = "cliente@correo.com")
    @NotBlank(message = "el email no puede estar vacio")
    @Email(message = "el email debe tener un formato valido")
    private String emailCliente;

    @Schema(description = "telefono del cliente", example = "099123456")
    @NotBlank(message = "el telefono no puede estar vacio")
    private String telefonoCliente;

    @Schema(description = "fecha del turno (yyyy-MM-dd)", example = "2026-10-15")
    @NotNull(message = "la fecha es obligatoria")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate fecha;

    @Schema(description = "hora del turno (HH:mm)", example = "10:00")
    @NotNull(message = "la hora es obligatoria")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    private LocalTime hora;

    public CrearReservaDTO() {}

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
