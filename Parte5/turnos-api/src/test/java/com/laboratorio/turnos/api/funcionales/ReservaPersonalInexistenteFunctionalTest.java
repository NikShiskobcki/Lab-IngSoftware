package com.laboratorio.turnos.api.funcionales;

import com.laboratorio.turnos.api.establecimiento.EstablecimientoRepository;
import com.laboratorio.turnos.api.exception.ResourceNotFoundException;
import com.laboratorio.turnos.api.personal.PersonalConsultaService;
import com.laboratorio.turnos.api.personal.PersonalController;
import com.laboratorio.turnos.api.personal.PersonalRepository;
import com.laboratorio.turnos.api.repository.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * IS-74 / US-63 - Prueba Reserva a Personal Inexistente

 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("US-63 | Prueba Personal Inexistente (Nivel API)")
public class ReservaPersonalInexistenteFunctionalTest {

    @Mock
    private PersonalRepository personalRepository;
    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private EstablecimientoRepository establecimientoRepository;

    private PersonalConsultaService consultaService;
    private PersonalController controller;

    @BeforeEach
    void setUp() {
        consultaService = new PersonalConsultaService(personalRepository, reservaRepository, establecimientoRepository);
        controller = new PersonalController(personalRepository, establecimientoRepository, consultaService);
    }

    @Test
    @DisplayName("CP-US63-API-02 | Consultar disponibilidad para personal inexistente lanza ResourceNotFoundException (404)")
    void consultarDisponibilidadPersonalInexistente() {
        when(personalRepository.findById(9999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> controller.consultarDisponibilidad(9999L, LocalDate.now().plusDays(2)));
    }
}
