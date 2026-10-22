package com.laboratorio.turnos.validador.service;

import com.laboratorio.turnos.validador.model.ReservaTurno;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * US-62 - Prueba doble reserva (Evitar doble reserva)
 */
@DisplayName("US-62 | Prueba doble reserva (Caja Negra)")
@MockitoSettings(strictness = Strictness.LENIENT)
public class DobleReservaFunctionalTest extends TurnoValidadorTestBase {

    private static final String AGENDADO = "Agendado";
    private static final String OCUPADO = "Rechazado/Turno Ocupado";

    // ---------- Dos Solicitudes Idénticas Simultáneas (TD: Regla Colisión Exacta) ----------
    @Test
    @DisplayName("CP-US62-01 | TD: Dos solicitudes simultáneas para el mismo horario -> La 1ra se agenda y la 2da se rechaza como Ocupado")
    void dobleReservaMismoHorario() {
        LocalDate fecha = futuro();
        LocalTime hora = LocalTime.of(10, 0);

        ReservaTurno primera = turno(101, fecha, hora, "SOLICITADO");
        ReservaTurno segunda = turno(102, fecha, hora, "SOLICITADO");

        // Simular que el repositorio devuelve la reserva por ID
        when(reservaRepo.findById(101)).thenReturn(Optional.of(primera));
        when(reservaRepo.findById(102)).thenReturn(Optional.of(segunda));

        // Al procesar la primera, la base no tiene turnos previos ese día
        when(reservaRepo.findByIdPersonalAndFechaTurno(eq(ID_PERSONAL), eq(fecha)))
                .thenReturn(new ArrayList<>()) // Para la primera
                .thenReturn(new ArrayList<>(List.of(primera))); // Para la segunda (la primera ya quedó agendada)

        // Encolar ambas solicitudes
        cola.agregar(101);
        cola.agregar(102);

        // Ejecutar el ciclo de validación
        service.revisarTurnosNuevos();

        // Verificaciones
        assertEquals(AGENDADO, primera.getEstado(), "La primera reserva debe quedar Agendada");
        assertEquals(OCUPADO, segunda.getEstado(), "La segunda reserva duplicada debe ser rechazada como Rechazado/Turno Ocupado");

        verify(reservaRepo).save(primera);
        verify(reservaRepo).save(segunda);
    }

    // ---------- CP-US62-02: AVL - Turnos Contiguos Inmediatos (Sin Solapamiento) ----------
    @Test
    @DisplayName("CP-US62-02 | AVL: Turnos contiguos inmediatos (10:00-10:30 y 10:30-11:00) no colisionan y se agendan ambos")
    void turnosContiguosFronteraExacta() {
        LocalDate fecha = futuro();
        LocalTime hora1 = LocalTime.of(10, 0);
        LocalTime hora2 = LocalTime.of(10, 30);

        ReservaTurno t1 = turno(201, fecha, hora1, "SOLICITADO");
        ReservaTurno t2 = turno(202, fecha, hora2, "SOLICITADO");

        when(reservaRepo.findById(201)).thenReturn(Optional.of(t1));
        when(reservaRepo.findById(202)).thenReturn(Optional.of(t2));

        when(reservaRepo.findByIdPersonalAndFechaTurno(eq(ID_PERSONAL), eq(fecha)))
                .thenReturn(new ArrayList<>())
                .thenReturn(new ArrayList<>(List.of(t1)));

        cola.agregar(201);
        cola.agregar(202);

        service.revisarTurnosNuevos();

        assertEquals(AGENDADO, t1.getEstado(), "El primer bloque debe quedar Agendado");
        assertEquals(AGENDADO, t2.getEstado(), "El bloque inmediatamente contiguo debe quedar Agendado");
    }

    // ---------- CP-US62-03: Solapamiento Parcial (TD: Regla Solapamiento de Intervalo) ----------
    @Test
    @DisplayName("CP-US62-03 | TD: Solapamiento parcial de horarios en la misma ventana de 30 min es rechazado como Ocupado")
    void solapamientoParcial() {
        LocalDate fecha = futuro();
        LocalTime horaBase = LocalTime.of(10, 0);
        LocalTime horaSolapada = LocalTime.of(10, 15); // Solapa con [10:00, 10:30)

        ReservaTurno turnoExistente = turno(301, fecha, horaBase, "Agendado");
        ReservaTurno nuevaSolicitud = turno(302, fecha, horaSolapada, "SOLICITADO");

        when(reservaRepo.findById(302)).thenReturn(Optional.of(nuevaSolicitud));
        when(reservaRepo.findByIdPersonalAndFechaTurno(eq(ID_PERSONAL), eq(fecha)))
                .thenReturn(new ArrayList<>(List.of(turnoExistente)));

        cola.agregar(302);
        service.revisarTurnosNuevos();

        assertEquals(OCUPADO, nuevaSolicitud.getEstado(),
                "Debe rechazarse como Rechazado/Turno Ocupado por solapamiento parcial de horario");
    }
}
