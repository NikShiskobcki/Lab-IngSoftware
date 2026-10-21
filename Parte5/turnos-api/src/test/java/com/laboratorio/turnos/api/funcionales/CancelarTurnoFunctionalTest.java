package com.laboratorio.turnos.api.funcionales;

import com.laboratorio.turnos.api.controller.ReservaController;
import com.laboratorio.turnos.api.establecimiento.Establecimiento;
import com.laboratorio.turnos.api.establecimiento.EstablecimientoRepository;
import com.laboratorio.turnos.api.model.Reserva;
import com.laboratorio.turnos.api.personal.EstadoPersonal;
import com.laboratorio.turnos.api.personal.Personal;
import com.laboratorio.turnos.api.personal.PersonalConsultaService;
import com.laboratorio.turnos.api.personal.PersonalRepository;
import com.laboratorio.turnos.api.personal.dto.DisponibilidadResponse;
import com.laboratorio.turnos.api.repository.ReservaRepository;
import com.laboratorio.turnos.api.service.MqttPublisherService;
import com.laboratorio.turnos.api.service.ReservaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * IS-71 / US-60 - Prueba Cancelar Turno
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("US-60 | Prueba Cancelar Turno ")
public class CancelarTurnoFunctionalTest {

    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private MqttPublisherService mqttPublisherService;
    @Mock
    private PersonalRepository personalRepository;
    @Mock
    private EstablecimientoRepository establecimientoRepository;

    private ReservaService reservaService;
    private ReservaController controller;
    private PersonalConsultaService personalConsultaService;

    private final LocalDate FECHA_PRUEBA = LocalDate.now().plusDays(4);

    @BeforeEach
    void setUp() {
        reservaService = new ReservaService(reservaRepository, mqttPublisherService);
        controller = new ReservaController(reservaService);
        personalConsultaService = new PersonalConsultaService(personalRepository, reservaRepository, establecimientoRepository);

        // Profesional base
        Personal p = new Personal(1L, 10L, "Dr. Martin", "Cardiologia", new BigDecimal("1800"), 30, EstadoPersonal.ACTIVO);
        when(personalRepository.findById(1L)).thenReturn(Optional.of(p));

        // Establecimiento base (08:00 a 10:00 -> 4 slots: 08:00, 08:30, 09:00, 09:30)
        Establecimiento e = new Establecimiento();
        e.setId(10L);
        e.setHorarioApertura(LocalTime.of(8, 0));
        e.setHorarioCierre(LocalTime.of(10, 0));
        when(establecimientoRepository.findById(10L)).thenReturn(Optional.of(e));
    }

    private Reserva crearReservaMock(Integer id, String estado, LocalTime hora) {
        Reserva r = new Reserva();
        r.setId(id);
        r.setIdPersonal(1);
        r.setIdEstablecimiento(10);
        r.setFechaTurno(FECHA_PRUEBA);
        r.setHoraTurno(hora);
        r.setDuracionMinutos(30);
        r.setEstado(estado);
        return r;
    }

    // ---------- CP-US60-01: Cancelar Turno en Estado Agendado (204 No Content) ----------
    @Test
    @DisplayName("CP-US60-01 | Cancelar turno en estado 'Agendado' retorna HTTP 204 No Content")
    void cancelarTurno_estadoAgendado_retorna204() {
        Reserva turno = crearReservaMock(15, "Agendado", LocalTime.of(9, 0));
        when(reservaRepository.findById(15)).thenReturn(Optional.of(turno));

        ResponseEntity<?> respuesta = controller.eliminarReserva(15);

        assertEquals(HttpStatus.NO_CONTENT, respuesta.getStatusCode());
        verify(reservaRepository, times(1)).delete(turno);
    }

    // ---------- CP-US60-02: Cancelar Turno en Estado SOLICITADO (204 No Content) ----------
    @Test
    @DisplayName("CP-US60-02 | Cancelar turno en estado 'SOLICITADO' retorna HTTP 204 No Content")
    void cancelarTurno_estadoSolicitado_retorna204() {
        Reserva turno = crearReservaMock(16, "SOLICITADO", LocalTime.of(9, 30));
        when(reservaRepository.findById(16)).thenReturn(Optional.of(turno));

        ResponseEntity<?> respuesta = controller.eliminarReserva(16);

        assertEquals(HttpStatus.NO_CONTENT, respuesta.getStatusCode());
        verify(reservaRepository).delete(turno);
    }

    // ---------- CP-US60-03: Cancelar Turno Inexistente (404 Not Found) ----------
    @Test
    @DisplayName("CP-US60-03 | Intentar cancelar un turno inexistente retorna HTTP 404 Not Found")
    void cancelarTurno_inexistente_retorna404() {
        when(reservaRepository.findById(999)).thenReturn(Optional.empty());

        ResponseEntity<?> respuesta = controller.eliminarReserva(999);

        assertEquals(HttpStatus.NOT_FOUND, respuesta.getStatusCode());
        verify(reservaRepository, never()).delete(any());
    }

    // ---------- CP-US60-06: Liberación del Horario tras Cancelación ----------
    @Test
    @DisplayName("CP-US60-06 | Efecto de negocio: Al cancelar el turno, el horario vuelve a estar disponible")
    void cancelarTurno_liberaHorarioEnDisponibilidad() {
        LocalTime horaOcupada = LocalTime.of(8, 30);
        Reserva turno = crearReservaMock(30, "CONFIRMADO", horaOcupada);

        // Antes de cancelar: horario 08:30 está ocupado
        when(reservaRepository.findByIdPersonalAndFechaTurnoOrderByHoraTurnoAsc(1, FECHA_PRUEBA))
                .thenReturn(List.of(turno));
        DisponibilidadResponse dispAntes = personalConsultaService.consultarDisponibilidad(1L, FECHA_PRUEBA);
        assertFalse(dispAntes.getHorariosDisponibles().contains(horaOcupada),
                "El horario 08:30 debe estar ocupado antes de la cancelación");

        // Acción de cancelación
        when(reservaRepository.findById(30)).thenReturn(Optional.of(turno));
        controller.eliminarReserva(30);

        // Después de cancelar: el repositorio ya no devuelve la reserva
        when(reservaRepository.findByIdPersonalAndFechaTurnoOrderByHoraTurnoAsc(1, FECHA_PRUEBA))
                .thenReturn(new ArrayList<>());
        DisponibilidadResponse dispDespues = personalConsultaService.consultarDisponibilidad(1L, FECHA_PRUEBA);
        assertTrue(dispDespues.getHorariosDisponibles().contains(horaOcupada),
                "El horario 08:30 debe quedar liberado y disponible nuevamente");
    }
}
