package com.laboratorio.turnos.validador.service;

import com.laboratorio.turnos.validador.model.ReservaTurno;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * IS-76 / US-65 - Prueba Reserva Fuera de horario
 */
@DisplayName("US-65 | Prueba Reserva Fuera de horario")
@MockitoSettings(strictness = Strictness.LENIENT)
public class ReservaFueraDeHorarioFunctionalTest extends TurnoValidadorTestBase {

    private static final String AGENDADO = "Agendado";

    // ---------- CP-US65-04: - Fecha y Hora Futura Válida ----------
    @Test
    @DisplayName("CP-US65-04 | AVL: Reserva en fecha y hora estrictamente futura -> Agendado")
    void reservaFutura() {
        LocalDate futuro = LocalDate.now().plusDays(2);
        LocalTime horaValida = LocalTime.of(10, 30);

        ReservaTurno t = turno(604, futuro, horaValida, "SOLICITADO");
        when(reservaRepo.findById(604)).thenReturn(Optional.of(t));

        cola.agregar(604);
        service.revisarTurnosNuevos();

        assertEquals(AGENDADO, t.getEstado(), "Una reserva estrictamente en el futuro debe quedar Agendada");
    }
}
