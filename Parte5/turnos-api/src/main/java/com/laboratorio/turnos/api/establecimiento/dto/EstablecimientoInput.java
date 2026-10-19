package com.laboratorio.turnos.api.establecimiento.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public class EstablecimientoInput {

    @NotBlank(message = "nombreComercial es obligatorio")
    private String nombreComercial;

    @NotBlank(message = "direccion es obligatoria")
    private String direccion;

    @NotBlank(message = "telefono es obligatorio")
    private String telefono;

    @NotBlank(message = "correoElectronico es obligatorio")
    @Email(message = "correoElectronico debe ser un email válido")
    private String correoElectronico;

    @NotNull(message = "horarioApertura es obligatorio")
    private LocalTime horarioApertura;

    @NotNull(message = "horarioCierre es obligatorio")
    private LocalTime horarioCierre;

    public String getNombreComercial() { return nombreComercial; }
    public void setNombreComercial(String nombreComercial) { this.nombreComercial = nombreComercial; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getCorreoElectronico() { return correoElectronico; }
    public void setCorreoElectronico(String correoElectronico) { this.correoElectronico = correoElectronico; }

    public LocalTime getHorarioApertura() { return horarioApertura; }
    public void setHorarioApertura(LocalTime horarioApertura) { this.horarioApertura = horarioApertura; }

    public LocalTime getHorarioCierre() { return horarioCierre; }
    public void setHorarioCierre(LocalTime horarioCierre) { this.horarioCierre = horarioCierre; }
}
