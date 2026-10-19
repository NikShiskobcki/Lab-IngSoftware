package com.laboratorio.turnos.api.reglas;

import com.laboratorio.turnos.api.establecimiento.Establecimiento;
import com.laboratorio.turnos.api.establecimiento.EstablecimientoController;
import com.laboratorio.turnos.api.establecimiento.EstablecimientoRepository;
import com.laboratorio.turnos.api.establecimiento.dto.EstablecimientoInput;
import com.laboratorio.turnos.api.exception.ResourceNotFoundException;
import com.laboratorio.turnos.api.personal.EstadoPersonal;
import com.laboratorio.turnos.api.personal.Personal;
import com.laboratorio.turnos.api.personal.PersonalConsultaService;
import com.laboratorio.turnos.api.personal.PersonalController;
import com.laboratorio.turnos.api.personal.PersonalRepository;
import com.laboratorio.turnos.api.personal.dto.PersonalInput;
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
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * US-52 - Reglas de negocio de los controladores de Establecimiento y Personal.
 * Los repositorios son mocks: no hay base de datos ni contexto Spring.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("US-52 | Reglas de negocio: Establecimiento y Personal")
class EstablecimientoYPersonalReglasTest {

    @Mock
    private EstablecimientoRepository establecimientoRepository;
    @Mock
    private PersonalRepository personalRepository;
    @Mock
    private PersonalConsultaService consultaService;

    // ---------------- Establecimiento ----------------

    private EstablecimientoInput establecimientoInput(LocalTime apertura, LocalTime cierre) {
        EstablecimientoInput in = new EstablecimientoInput();
        in.setNombreComercial("Clinica Central");
        in.setDireccion("Av. Principal 123");
        in.setTelefono("42221234");
        in.setCorreoElectronico("contacto@clinica.com");
        in.setHorarioApertura(apertura);
        in.setHorarioCierre(cierre);
        return in;
    }

    @Test
    @DisplayName("Un establecimiento con cierre posterior a la apertura se registra (201)")
    void establecimiento_horarioCoherente() {
        EstablecimientoController controller = new EstablecimientoController(establecimientoRepository);
        when(establecimientoRepository.save(any(Establecimiento.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<Establecimiento> resp =
                controller.crear(establecimientoInput(LocalTime.of(9, 0), LocalTime.of(18, 0)));

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals(LocalTime.of(9, 0), resp.getBody().getHorarioApertura());
        assertEquals(LocalTime.of(18, 0), resp.getBody().getHorarioCierre());
        verify(establecimientoRepository).save(any(Establecimiento.class));
    }

    @Test
    @DisplayName("Cierre anterior a la apertura es rechazado y no se guarda")
    void establecimiento_cierreAnteriorAApertura() {
        EstablecimientoController controller = new EstablecimientoController(establecimientoRepository);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> controller.crear(establecimientoInput(LocalTime.of(18, 0), LocalTime.of(9, 0))));

        assertTrue(ex.getMessage().contains("horarioCierre"));
        verify(establecimientoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cierre igual a la apertura es rechazado (agenda de duracion cero)")
    void establecimiento_cierreIgualAApertura() {
        EstablecimientoController controller = new EstablecimientoController(establecimientoRepository);

        assertThrows(IllegalArgumentException.class,
                () -> controller.crear(establecimientoInput(LocalTime.of(9, 0), LocalTime.of(9, 0))));
        verify(establecimientoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Actualizar un establecimiento inexistente falla con ResourceNotFoundException")
    void establecimiento_actualizarInexistente() {
        EstablecimientoController controller = new EstablecimientoController(establecimientoRepository);
        when(establecimientoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> controller.actualizar(99L, establecimientoInput(LocalTime.of(9, 0), LocalTime.of(18, 0))));
        verify(establecimientoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Eliminar un establecimiento inexistente falla con ResourceNotFoundException")
    void establecimiento_eliminarInexistente() {
        EstablecimientoController controller = new EstablecimientoController(establecimientoRepository);
        when(establecimientoRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> controller.eliminar(99L));
        verify(establecimientoRepository, never()).deleteById(any());
    }

    // ---------------- Personal ----------------

    private PersonalController personalController() {
        return new PersonalController(personalRepository, establecimientoRepository, consultaService);
    }

    private PersonalInput personalInput(Long idEstablecimiento) {
        PersonalInput in = new PersonalInput();
        in.setIdEstablecimiento(idEstablecimiento);
        in.setNombre("Dra. Ana Perez");
        in.setEspecialidad("Pediatria");
        in.setCostoConsulta(new BigDecimal("1500"));
        in.setDuracionEstandarMinutos(30);
        return in;
    }

    @Test
    @DisplayName("Registrar personal en un establecimiento inexistente es rechazado y no se guarda")
    void personal_establecimientoInexistente() {
        when(establecimientoRepository.existsById(5L)).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> personalController().crear(personalInput(5L)));

        assertTrue(ex.getMessage().contains("5"));
        verify(personalRepository, never()).save(any());
    }

    @Test
    @DisplayName("Registrar personal en un establecimiento existente funciona (201) y queda ACTIVO por defecto")
    void personal_establecimientoExistente_quedaActivo() {
        when(establecimientoRepository.existsById(1L)).thenReturn(true);
        when(personalRepository.save(any(Personal.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<Personal> resp = personalController().crear(personalInput(1L));

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals(EstadoPersonal.ACTIVO, resp.getBody().getEstado());
        assertEquals(1L, resp.getBody().getIdEstablecimiento());
    }

    @Test
    @DisplayName("Si se informa estado INACTIVO se persiste como INACTIVO (el validador lo rechazara al reservar)")
    void personal_estadoInactivoExplicito() {
        when(establecimientoRepository.existsById(1L)).thenReturn(true);
        when(personalRepository.save(any(Personal.class))).thenAnswer(i -> i.getArgument(0));
        PersonalInput in = personalInput(1L);
        in.setEstado(EstadoPersonal.INACTIVO);

        ResponseEntity<Personal> resp = personalController().crear(in);

        assertEquals(EstadoPersonal.INACTIVO, resp.getBody().getEstado());
    }

    @Test
    @DisplayName("Actualizar personal inexistente falla con ResourceNotFoundException")
    void personal_actualizarInexistente() {
        when(personalRepository.findById(8L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> personalController().actualizar(8L, personalInput(1L)));
        verify(personalRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reasignar personal a un establecimiento inexistente al actualizar es rechazado")
    void personal_actualizarAEstablecimientoInexistente() {
        when(personalRepository.findById(8L)).thenReturn(Optional.of(new Personal()));
        when(establecimientoRepository.existsById(5L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> personalController().actualizar(8L, personalInput(5L)));
        verify(personalRepository, never()).save(any());
    }

    @Test
    @DisplayName("Eliminar personal inexistente falla con ResourceNotFoundException")
    void personal_eliminarInexistente() {
        when(personalRepository.existsById(8L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> personalController().eliminar(8L));
        verify(personalRepository, never()).deleteById(any());
    }
}
