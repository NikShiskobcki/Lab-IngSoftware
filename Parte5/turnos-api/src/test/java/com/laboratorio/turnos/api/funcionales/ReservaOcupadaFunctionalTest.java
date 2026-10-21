package com.laboratorio.turnos.api.funcionales;

import com.laboratorio.turnos.api.establecimiento.Establecimiento;
import com.laboratorio.turnos.api.establecimiento.EstablecimientoRepository;
import com.laboratorio.turnos.api.model.Reserva;
import com.laboratorio.turnos.api.personal.EstadoPersonal;
import com.laboratorio.turnos.api.personal.Personal;
import com.laboratorio.turnos.api.personal.PersonalConsultaService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * IS-75 / US-64 - Prueba Reserva ocupada (horario ocupado)

 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("US-64 | Prueba Reserva ocupada (Nivel API)")
public class ReservaOcupadaFunctionalTest {

    @Mock
    private PersonalRepository personalRepository;
    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private EstablecimientoRepository establecimientoRepository;

    private PersonalConsultaService service;
    private final LocalDate FECHA = LocalDate.now().plusDays(2);

    @BeforeEach
    void setUp() {
        service = new PersonalConsultaService(personalRepository, reservaRepository, establecimientoRepository);

        Personal p = new Personal(1L, 1L, "Dr. Ocupado", "Clinica", new BigDecimal("1000"), 30, EstadoPersonal.ACTIVO);
        when(personalRepository.findById(1L)).thenReturn(Optional.of(p));

        Establecimiento e = new Establecimiento();
        e.setId(1L);
        e.setHorarioApertura(LocalTime.of(8, 0));
        e.setHorarioCierre(LocalTime.of(12, 0));
        when(establecimientoRepository.findById(1L)).thenReturn(Optional.of(e));
    }

    @Test
    @DisplayName("CP-US64-API-01 | Un slot con reserva en CONFIRMADO es excluido de los horarios disponibles")
    void slotOcupado_noApareceEnDisponibilidad() {
        LocalTime slotOcupado = LocalTime.of(9, 0);

        Reserva r = new Reserva();
        r.setId(10);
        r.setIdPersonal(1);
        r.setFechaTurno(FECHA);
        r.setHoraTurno(slotOcupado);
        r.setDuracionMinutos(30);
        r.setEstado("CONFIRMADO");

        when(reservaRepository.findByIdPersonalAndFechaTurnoOrderByHoraTurnoAsc(1, FECHA))
                .thenReturn(List.of(r));

        DisponibilidadResponse disp = service.consultarDisponibilidad(1L, FECHA);

        assertFalse(disp.getHorariosDisponibles().contains(slotOcupado),
                "El horario 09:00 está confirmado y no debe ofrecerse como disponible");
        assertTrue(disp.getHorariosDisponibles().contains(LocalTime.of(8, 30)));
        assertTrue(disp.getHorariosDisponibles().contains(LocalTime.of(9, 30)));
    }
}
