package com.laboratorio.turnos.validador.service;

import com.laboratorio.turnos.validador.model.ReservaTurno;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * US-52 - Reglas de negocio del validador, una por una y de forma aislada.
 * Cada test deja un unico turno SOLICITADO en la cola, ejecuta un ciclo y comprueba el estado resultante.
 */
@DisplayName("US-52 | Reglas de negocio: validador de turnos")
@MockitoSettings(strictness = Strictness.LENIENT)
class TurnoValidadorReglasNegocioTest extends TurnoValidadorTestBase {

    private static final String AGENDADO = "Agendado";
    private static final String NO_VALIDA = "Rechazado/Solicitud No Valida";
    private static final String OCUPADO = "Rechazado/Turno Ocupado";

    private ReservaTurno solicitar(LocalDate fecha, LocalTime hora) {
        return recibir(turno(100, fecha, hora, "SOLICITADO"));
    }

    // ---------- validacion de establecimiento / personal existente ----------

    @Test
    @DisplayName("Profesional inexistente -> Rechazado/Solicitud No Valida")
    void profesionalInexistente() {
        ReservaTurno t = solicitar(futuro(), LocalTime.of(10, 0));
        when(personalRepo.findById(ID_PERSONAL)).thenReturn(Optional.empty());

        service.revisarTurnosNuevos();

        assertEquals(NO_VALIDA, t.getEstado());
        verify(reservaRepo).save(t);
    }

    @Test
    @DisplayName("Establecimiento inexistente -> Rechazado/Solicitud No Valida")
    void establecimientoInexistente() {
        ReservaTurno t = solicitar(futuro(), LocalTime.of(10, 0));
        t.setIdEstablecimiento(9);
        when(personalRepo.findById(ID_PERSONAL)).thenReturn(Optional.of(personal("ACTIVO", 9)));
        when(establecimientoRepo.existsById(9)).thenReturn(false);

        service.revisarTurnosNuevos();

        assertEquals(NO_VALIDA, t.getEstado());
    }

    @Test
    @DisplayName("Profesional que pertenece a otro establecimiento -> Rechazado/Solicitud No Valida")
    void profesionalDeOtroEstablecimiento() {
        ReservaTurno t = solicitar(futuro(), LocalTime.of(10, 0));
        t.setIdEstablecimiento(2); // el profesional es del establecimiento 1
        when(establecimientoRepo.existsById(2)).thenReturn(true);

        service.revisarTurnosNuevos();

        assertEquals(NO_VALIDA, t.getEstado());
    }

    @Test
    @DisplayName("Si la solicitud no trae establecimiento se usa el del profesional y se agenda")
    void sinEstablecimientoSeUsaElDelProfesional() {
        ReservaTurno t = solicitar(futuro(), LocalTime.of(10, 0));
        t.setIdEstablecimiento(null);

        service.revisarTurnosNuevos();

        assertEquals(AGENDADO, t.getEstado());
    }

    // ---------- personal inactivo ----------

    @ParameterizedTest(name = "Profesional en estado {0} -> Rechazado/Solicitud No Valida")
    @ValueSource(strings = {"INACTIVO", "inactivo", "SUSPENDIDO", ""})
    void personalInactivo(String estado) {
        ReservaTurno t = solicitar(futuro(), LocalTime.of(10, 0));
        when(personalRepo.findById(ID_PERSONAL)).thenReturn(Optional.of(personal(estado, ID_ESTABLECIMIENTO)));

        service.revisarTurnosNuevos();

        assertEquals(NO_VALIDA, t.getEstado());
    }

    @Test
    @DisplayName("El estado ACTIVO se reconoce sin distinguir mayusculas")
    void personalActivoSinDistinguirMayusculas() {
        ReservaTurno t = solicitar(futuro(), LocalTime.of(10, 0));
        when(personalRepo.findById(ID_PERSONAL)).thenReturn(Optional.of(personal("activo", ID_ESTABLECIMIENTO)));

        service.revisarTurnosNuevos();

        assertEquals(AGENDADO, t.getEstado());
    }

    // ---------- turno de fecha pasada ----------

    @Test
    @DisplayName("Turno de un dia pasado -> Rechazado/Solicitud No Valida")
    void turnoDeFechaPasada() {
        ReservaTurno t = solicitar(LocalDate.now().minusDays(1), LocalTime.of(10, 0));

        service.revisarTurnosNuevos();

        assertEquals(NO_VALIDA, t.getEstado());
    }

    @Test
    @DisplayName("Turno de hoy con una hora que ya paso -> Rechazado/Solicitud No Valida")
    void turnoDeHoyConHoraPasada() {
        LocalDateTime haceUnRato = LocalDateTime.now().minusMinutes(10);
        ReservaTurno t = solicitar(haceUnRato.toLocalDate(), haceUnRato.toLocalTime());

        service.revisarTurnosNuevos();

        assertEquals(NO_VALIDA, t.getEstado());
    }

    @Test
    @DisplayName("Turno futuro -> se agenda")
    void turnoFuturo() {
        ReservaTurno t = solicitar(futuro(), LocalTime.of(10, 0));

        service.revisarTurnosNuevos();

        assertEquals(AGENDADO, t.getEstado());
    }

    // ---------- no solapamiento de turnos del mismo profesional + duracion fija de 30 min ----------

    @Test
    @DisplayName("Mismo profesional, mismo dia y misma hora que un turno Agendado -> Rechazado/Turno Ocupado")
    void mismoHorarioQueOtroAgendado() {
        LocalDate dia = futuro();
        turnosExistentes(dia, turno(1, dia, LocalTime.of(10, 0), "Agendado"));
        ReservaTurno t = solicitar(dia, LocalTime.of(10, 0));

        service.revisarTurnosNuevos();

        assertEquals(OCUPADO, t.getEstado());
    }

    /**
     * Existe un turno Agendado de 10:00 a 10:30 (duracion fija de 30 min).
     * Se solicita otro de 30 min a la hora indicada.
     */
    @ParameterizedTest(name = "Solicitud a las {0} con turno existente 10:00-10:30 -> {1}")
    @CsvSource({
            "09:30, Agendado",                  // termina justo cuando empieza el otro: no se pisan
            "09:45, Rechazado/Turno Ocupado",   // se pisa 15 min con el inicio del existente
            "10:00, Rechazado/Turno Ocupado",   // mismo horario
            "10:15, Rechazado/Turno Ocupado",   // se pisa 15 min con el final del existente
            "10:30, Agendado",                  // empieza justo cuando termina el otro: no se pisan
            "11:00, Agendado"                   // totalmente separado
    })
    void solapamientoConBloquesDeTreintaMinutos(String hora, String estadoEsperado) {
        LocalDate dia = futuro();
        turnosExistentes(dia, turno(1, dia, LocalTime.of(10, 0), "Agendado"));
        ReservaTurno t = solicitar(dia, LocalTime.parse(hora));

        service.revisarTurnosNuevos();

        assertEquals(estadoEsperado, t.getEstado());
    }

    @ParameterizedTest(name = "Un turno existente en estado {0} ocupa el horario")
    @ValueSource(strings = {"Agendado", "Atendido", "Facturado", "agendado"})
    void estadosQueOcupanElHorario(String estadoExistente) {
        LocalDate dia = futuro();
        turnosExistentes(dia, turno(1, dia, LocalTime.of(10, 0), estadoExistente));
        ReservaTurno t = solicitar(dia, LocalTime.of(10, 0));

        service.revisarTurnosNuevos();

        assertEquals(OCUPADO, t.getEstado());
    }

    // ---------- horario libre considerando turnos cancelados / no vigentes ----------

    @ParameterizedTest(name = "Un turno existente en estado {0} NO ocupa el horario")
    @ValueSource(strings = {"Cancelado", "CANCELADO", "Rechazado/Turno Ocupado", "Rechazado/Solicitud No Valida", "SOLICITADO"})
    void estadosQueNoOcupanElHorario(String estadoExistente) {
        LocalDate dia = futuro();
        turnosExistentes(dia, turno(1, dia, LocalTime.of(10, 0), estadoExistente));
        ReservaTurno t = solicitar(dia, LocalTime.of(10, 0));

        service.revisarTurnosNuevos();

        assertEquals(AGENDADO, t.getEstado());
    }

    @Test
    @DisplayName("Una modificacion (ACTUALIZAR) no choca consigo misma")
    void unaReservaNoChocaConsigoMisma() {
        LocalDate dia = futuro();
        ReservaTurno t = solicitar(dia, LocalTime.of(10, 0));
        // el repositorio devuelve la propia reserva (mismo id) con un estado que normalmente ocupa
        ReservaTurno consigoMisma = turno(100, dia, LocalTime.of(10, 0), "Agendado");
        turnosExistentes(dia, consigoMisma);

        service.revisarTurnosNuevos();

        assertEquals(AGENDADO, t.getEstado());
    }

    // ---------- eventos que no deben validarse ----------

    @Test
    @DisplayName("Un evento repetido de una reserva que ya no esta SOLICITADO se ignora (no se vuelve a validar)")
    void eventoRepetidoSeIgnora() {
        ReservaTurno t = recibir(turno(100, futuro(), LocalTime.of(10, 0), "Agendado"));

        service.revisarTurnosNuevos();

        assertEquals("Agendado", t.getEstado());
        verify(reservaRepo, never()).save(any(ReservaTurno.class));
    }

    @Test
    @DisplayName("Un evento de una reserva que ya no existe se ignora sin error")
    void reservaInexistenteSeIgnora() {
        when(reservaRepo.findById(555)).thenReturn(Optional.empty());
        cola.agregar(555);

        service.revisarTurnosNuevos();

        verify(reservaRepo, never()).save(any(ReservaTurno.class));
    }
}
