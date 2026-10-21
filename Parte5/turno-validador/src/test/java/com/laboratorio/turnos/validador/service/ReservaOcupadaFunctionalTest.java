package com.laboratorio.turnos.validador.service;

import com.laboratorio.turnos.validador.model.ReservaTurno;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IS-75 / US-64 - Prueba Reserva ocupada (horario ocupado)
 */
@DisplayName("US-64 | Prueba Reserva ocupada (Caja Negra)")
@MockitoSettings(strictness = Strictness.LENIENT)
public class ReservaOcupadaFunctionalTest extends TurnoValidadorTestBase {

    private static final String AGENDADO = "Agendado";
    private static final String OCUPADO = "Rechazado/Turno Ocupado";

    // ---------- CP-US64-01: Colisión con Turno Agendado (TD: Estado Agendado) ----------
    @Test
    @DisplayName("CP-US64-01 | TD: Reserva para horario ocupado por turno 'Agendado' -> Pasa a 'Rechazado/Turno Ocupado'")
    void reservaHorarioOcupado_porTurnoAgendado_cambiaARechazadoTurnoOcupado() {
        LocalDate fecha = futuro();
        LocalTime hora = LocalTime.of(10, 0);

        ReservaTurno existente = turno(501, fecha, hora, "Agendado");
        ReservaTurno nuevaSolicitud = turno(502, fecha, hora, "SOLICITADO");

        when(reservaRepo.findById(502)).thenReturn(Optional.of(nuevaSolicitud));
        when(reservaRepo.findByIdPersonalAndFechaTurno(eq(ID_PERSONAL), eq(fecha)))
                .thenReturn(new ArrayList<>(List.of(existente)));

        cola.agregar(502);
        service.revisarTurnosNuevos();

        assertEquals(OCUPADO, nuevaSolicitud.getEstado(),
                "Debe ser rechazada con estado Rechazado/Turno Ocupado");
        verify(reservaRepo).save(nuevaSolicitud);
    }

    // ---------- CP-US64-02: Colisión con Estados Terminales Bloqueantes (TD: Atendido y Facturado) ----------
    @ParameterizedTest(name = "CP-US64-02 | TD: Horario ocupado por turno previo en estado '{0}' -> Rechazado/Turno Ocupado")
    @ValueSource(strings = {"Atendido", "Facturado"})
    @DisplayName("CP-US64-02 | TD: Turnos previos en 'Atendido' o 'Facturado' también bloquean el horario")
    void reservaHorarioOcupado_porTurnosAtendidosOFacturados_rechazaComoOcupado(String estadoBloqueante) {
        LocalDate fecha = futuro();
        LocalTime hora = LocalTime.of(11, 30);

        ReservaTurno existente = turno(503, fecha, hora, estadoBloqueante);
        ReservaTurno nuevaSolicitud = turno(504, fecha, hora, "SOLICITADO");

        when(reservaRepo.findById(504)).thenReturn(Optional.of(nuevaSolicitud));
        when(reservaRepo.findByIdPersonalAndFechaTurno(eq(ID_PERSONAL), eq(fecha)))
                .thenReturn(new ArrayList<>(List.of(existente)));

        cola.agregar(504);
        service.revisarTurnosNuevos();

        assertEquals(OCUPADO, nuevaSolicitud.getEstado(),
                "El horario ocupado históricamente no debe poder reutilizarse");
    }

    // ---------- CP-US64-03: Horario Previo Rechazado o Cancelado (TD: Estados No Bloqueantes) ----------
    @ParameterizedTest(name = "CP-US64-03 | TD: Turno previo en estado '{0}' no bloquea el horario -> Nueva pasa a 'Agendado'")
    @ValueSource(strings = {
            "Cancelado",
            "Rechazado/Solicitud No Valida",
            "Rechazado/Turno Ocupado"
    })
    @DisplayName("CP-US64-03 | TD: Turnos cancelados o rechazados dejan el horario libre para nuevas reservas")
    void reservaHorario_turnoPrevioCanceladoORechazado_quedaAgendado(String estadoNoBloqueante) {
        LocalDate fecha = futuro();
        LocalTime hora = LocalTime.of(16, 0);

        ReservaTurno existente = turno(505, fecha, hora, estadoNoBloqueante);
        ReservaTurno nuevaSolicitud = turno(506, fecha, hora, "SOLICITADO");

        when(reservaRepo.findById(506)).thenReturn(Optional.of(nuevaSolicitud));
        when(reservaRepo.findByIdPersonalAndFechaTurno(eq(ID_PERSONAL), eq(fecha)))
                .thenReturn(new ArrayList<>(List.of(existente)));

        cola.agregar(506);
        service.revisarTurnosNuevos();

        assertEquals(AGENDADO, nuevaSolicitud.getEstado(),
                "Al estar liberado el slot por rechazo o cancelación, la nueva reserva debe quedar Agendada");
    }
}
