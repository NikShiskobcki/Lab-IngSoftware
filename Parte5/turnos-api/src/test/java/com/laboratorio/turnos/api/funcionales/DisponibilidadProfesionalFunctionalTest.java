package com.laboratorio.turnos.api.funcionales;

import com.laboratorio.turnos.api.establecimiento.Establecimiento;
import com.laboratorio.turnos.api.establecimiento.EstablecimientoRepository;
import com.laboratorio.turnos.api.exception.ResourceNotFoundException;
import com.laboratorio.turnos.api.model.Reserva;
import com.laboratorio.turnos.api.personal.EstadoPersonal;
import com.laboratorio.turnos.api.personal.Personal;
import com.laboratorio.turnos.api.personal.PersonalConsultaService;
import com.laboratorio.turnos.api.personal.PersonalController;
import com.laboratorio.turnos.api.personal.PersonalRepository;
import com.laboratorio.turnos.api.personal.dto.DisponibilidadResponse;
import com.laboratorio.turnos.api.repository.ReservaRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * IS-72 / US-61 - Prueba Disponibilidad de un Profesional
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("US-61 | Prueba Disponibilidad de un Profesional")
public class DisponibilidadProfesionalFunctionalTest {

    @Mock
    private PersonalRepository personalRepository;
    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private EstablecimientoRepository establecimientoRepository;

    private PersonalConsultaService consultaService;
    private PersonalController controller;

    private final LocalDate FECHA_TEST = LocalDate.now().plusDays(3);

    @BeforeEach
    void setUp() {
        consultaService = new PersonalConsultaService(personalRepository, reservaRepository, establecimientoRepository);
        controller = new PersonalController(personalRepository, establecimientoRepository, consultaService);

        // Establecimiento con horario 08:00 a 11:00 (6 slots: 08:00, 08:30, 09:00, 09:30, 10:00, 10:30)
        Establecimiento e = new Establecimiento();
        e.setId(10L);
        e.setHorarioApertura(LocalTime.of(8, 0));
        e.setHorarioCierre(LocalTime.of(11, 0));
        when(establecimientoRepository.findById(10L)).thenReturn(Optional.of(e));
    }

    private Personal mockPersonal(Long id, EstadoPersonal estado) {
        Personal p = new Personal(id, 10L, "Dra. Laura", "Dermatologia", new BigDecimal("1600"), 30, estado);
        when(personalRepository.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    private Reserva mockReserva(LocalTime hora, String estado) {
        Reserva r = new Reserva();
        r.setId(1);
        r.setIdPersonal(1);
        r.setFechaTurno(FECHA_TEST);
        r.setHoraTurno(hora);
        r.setDuracionMinutos(30);
        r.setEstado(estado);
        r.setEmailSolicitante("paciente@correo.com");
        r.setTelefonoSolicitante("099000111");
        return r;
    }

    // ---------- CP-US61-01: Disponibilidad Total sin Ocupación (PE: Profesional Activo Libre) ----------
    @Test
    @DisplayName("CP-US61-01 | PE: Profesional ACTIVO sin reservas ofrece todos los slots de 30 min")
    void disponibilidad_profesionalActivoSinReservas_ofreceTodosLosSlots() {
        mockPersonal(1L, EstadoPersonal.ACTIVO);
        when(reservaRepository.findByIdPersonalAndFechaTurnoOrderByHoraTurnoAsc(1, FECHA_TEST))
                .thenReturn(Collections.emptyList());

        ResponseEntity<DisponibilidadResponse> respuesta = controller.consultarDisponibilidad(1L, FECHA_TEST);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        DisponibilidadResponse body = respuesta.getBody();
        assertNotNull(body);
        assertEquals(30, body.getDuracionTurnoMinutos());

        // Slots esperados entre 08:00 y 11:00: [08:00, 08:30, 09:00, 09:30, 10:00, 10:30]
        List<LocalTime> esperados = List.of(
                LocalTime.of(8, 0),
                LocalTime.of(8, 30),
                LocalTime.of(9, 0),
                LocalTime.of(9, 30),
                LocalTime.of(10, 0),
                LocalTime.of(10, 30)
        );
        assertEquals(esperados, body.getHorariosDisponibles());
    }

    // ---------- CP-US61-02: Disponibilidad con Slots Ocupados (Filtro de Ocupación) ----------
    @Test
    @DisplayName("CP-US61-02 | Slots ocupados por reservas confirmadas se excluyen de la disponibilidad")
    void disponibilidad_conReservasOcupadas_excluyeSlotsOcupados() {
        mockPersonal(1L, EstadoPersonal.ACTIVO);
        Reserva r1 = mockReserva(LocalTime.of(8, 30), "CONFIRMADO");
        Reserva r2 = mockReserva(LocalTime.of(10, 0), "CONFIRMADO");

        when(reservaRepository.findByIdPersonalAndFechaTurnoOrderByHoraTurnoAsc(1, FECHA_TEST))
                .thenReturn(List.of(r1, r2));

        ResponseEntity<DisponibilidadResponse> respuesta = controller.consultarDisponibilidad(1L, FECHA_TEST);

        DisponibilidadResponse body = respuesta.getBody();
        assertNotNull(body);

        // Los turnos de 08:30 y 10:00 NO deben aparecer
        assertFalse(body.getHorariosDisponibles().contains(LocalTime.of(8, 30)));
        assertFalse(body.getHorariosDisponibles().contains(LocalTime.of(10, 0)));

        // Los restantes deben seguir disponibles
        assertTrue(body.getHorariosDisponibles().contains(LocalTime.of(8, 0)));
        assertTrue(body.getHorariosDisponibles().contains(LocalTime.of(9, 0)));
        assertTrue(body.getHorariosDisponibles().contains(LocalTime.of(9, 30)));
        assertTrue(body.getHorariosDisponibles().contains(LocalTime.of(10, 30)));
    }

    // ---------- CP-US61-03: Profesional Inactivo (PE: Clase Inválida de Estado) ----------
    @Test
    @DisplayName("CP-US61-03 | PE: Profesional INACTIVO no tiene disponibilidad (lista vacía)")
    void disponibilidad_profesionalInactivo_retornaListaVacia() {
        mockPersonal(2L, EstadoPersonal.INACTIVO);

        ResponseEntity<DisponibilidadResponse> respuesta = controller.consultarDisponibilidad(2L, FECHA_TEST);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertNotNull(respuesta.getBody());
        assertTrue(respuesta.getBody().getHorariosDisponibles().isEmpty());
    }

    // ---------- CP-US61-04: Profesional Inexistente (404 Not Found) ----------
    @Test
    @DisplayName("CP-US61-04 | Consultar disponibilidad de profesional inexistente lanza ResourceNotFoundException (404)")
    void disponibilidad_profesionalInexistente_lanza404() {
        when(personalRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> controller.consultarDisponibilidad(999L, FECHA_TEST));
    }

    // ---------- CP-US61-06: AVL en Fronteras de Apertura y Cierre ----------
    @Test
    @DisplayName("CP-US61-06 | AVL: Primer slot coincide exactamente con apertura y último slot no sobrepasa cierre")
    void disponibilidad_valoresLimite_aperturaYCierre() {
        mockPersonal(1L, EstadoPersonal.ACTIVO);
        when(reservaRepository.findByIdPersonalAndFechaTurnoOrderByHoraTurnoAsc(1, FECHA_TEST))
                .thenReturn(Collections.emptyList());

        DisponibilidadResponse body = controller.consultarDisponibilidad(1L, FECHA_TEST).getBody();
        assertNotNull(body);

        List<LocalTime> slots = body.getHorariosDisponibles();
        assertFalse(slots.isEmpty());

        // Límite inferior exacto
        assertEquals(LocalTime.of(8, 0), slots.get(0));

        // Límite superior exacto: 10:30 (dura 30 min y finaliza a las 11:00, hora de cierre)
        assertEquals(LocalTime.of(10, 30), slots.get(slots.size() - 1));

        // Verificar que ningún slot inicia a las 11:00 o después
        assertFalse(slots.contains(LocalTime.of(11, 0)));
    }
}
