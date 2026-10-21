package com.laboratorio.turnos.api.funcionales;

import com.laboratorio.turnos.api.establecimiento.Establecimiento;
import com.laboratorio.turnos.api.establecimiento.EstablecimientoRepository;
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
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

/**
 * IS-76 / US-65 - Prueba Reserva Fuera de horario

 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("US-65 | Prueba Fuera de Horario (Nivel API)")
public class ReservaFueraDeHorarioFunctionalTest {

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

        Personal p = new Personal(1L, 1L, "Dr. Horarios", "Clinica", new BigDecimal("1000"), 30, EstadoPersonal.ACTIVO);
        when(personalRepository.findById(1L)).thenReturn(Optional.of(p));

        // Establecimiento abierto de 09:00 a 17:00
        Establecimiento e = new Establecimiento();
        e.setId(1L);
        e.setHorarioApertura(LocalTime.of(9, 0));
        e.setHorarioCierre(LocalTime.of(17, 0));
        when(establecimientoRepository.findById(1L)).thenReturn(Optional.of(e));

        when(reservaRepository.findByIdPersonalAndFechaTurnoOrderByHoraTurnoAsc(1, FECHA))
                .thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("CP-US65-API-01 | Los horarios antes de apertura o posteriores al cierre jamás se ofrecen como disponibles")
    void horariosFueraDeAperturaYCierre_noSeOfrecen() {
        DisponibilidadResponse disp = service.consultarDisponibilidad(1L, FECHA);

        // Antes de la apertura (09:00)
        assertFalse(disp.getHorariosDisponibles().contains(LocalTime.of(8, 0)));
        assertFalse(disp.getHorariosDisponibles().contains(LocalTime.of(8, 30)));

        // A la hora de cierre o posterior (17:00, 17:30, 18:00)
        assertFalse(disp.getHorariosDisponibles().contains(LocalTime.of(17, 0)));
        assertFalse(disp.getHorariosDisponibles().contains(LocalTime.of(17, 30)));
        assertFalse(disp.getHorariosDisponibles().contains(LocalTime.of(18, 0)));
    }
}
