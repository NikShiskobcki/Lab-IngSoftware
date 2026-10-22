package com.laboratorio.turnos.validador.service;

import com.laboratorio.turnos.validador.model.ReservaTurno;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IS-74 / US-63 - Prueba Reserva a Personal Inexistente
 */
@DisplayName("US-63 | Prueba Reserva a Personal Inexistente (Caja Negra)")
@MockitoSettings(strictness = Strictness.LENIENT)
public class ReservaPersonalInexistenteFunctionalTest extends TurnoValidadorTestBase {

    private static final String NO_VALIDA = "Rechazado/Solicitud No Valida";

    // ---------- CP-US63-01: Profesional Inexistente en la Base de Datos (PE: ID Inexistente) ----------
    @Test
    @DisplayName("CP-US63-01 | PE: Reserva con idPersonal inexistente -> Estado pasa a 'Rechazado/Solicitud No Valida'")
    void reservaPersonalInexistente() {
        ReservaTurno t = turno(401, futuro(), LocalTime.of(11, 0), "SOLICITADO");
        t.setIdPersonal(9999);

        when(reservaRepo.findById(401)).thenReturn(Optional.of(t));
        when(personalRepo.findById(9999)).thenReturn(Optional.empty()); // Profesional no existe

        cola.agregar(401);
        service.revisarTurnosNuevos();

        assertEquals(NO_VALIDA, t.getEstado(), "Debe marcarse Rechazado/Solicitud No Valida");
        verify(reservaRepo).save(t);
    }
}
