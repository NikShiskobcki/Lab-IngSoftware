package com.laboratorio.turnos.facturacion.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

// turno en estado atendido junto con los datos del personal necesarios para facturarlo
public record TurnoAtendido(
        int id,
        int idPersonal,
        String emailCliente,
        String telefonoCliente,
        LocalDate fechaTurno,
        LocalTime horaTurno,
        String nombrePersonal,
        String especialidad,
        BigDecimal costoConsulta) {
}
