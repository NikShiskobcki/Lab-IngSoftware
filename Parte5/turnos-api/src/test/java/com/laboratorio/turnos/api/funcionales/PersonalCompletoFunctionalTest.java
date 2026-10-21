package com.laboratorio.turnos.api.funcionales;

import com.laboratorio.turnos.api.establecimiento.EstablecimientoRepository;
import com.laboratorio.turnos.api.exception.ResourceNotFoundException;
import com.laboratorio.turnos.api.personal.EstadoPersonal;
import com.laboratorio.turnos.api.personal.Personal;
import com.laboratorio.turnos.api.personal.PersonalConsultaService;
import com.laboratorio.turnos.api.personal.PersonalController;
import com.laboratorio.turnos.api.personal.PersonalRepository;
import com.laboratorio.turnos.api.personal.dto.PersonalInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * IS-69 / US-58 - Pruebas Personal completo
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("US-58 | Pruebas Personal completo")
public class PersonalCompletoFunctionalTest {

    @Mock
    private PersonalRepository personalRepository;
    @Mock
    private EstablecimientoRepository establecimientoRepository;
    @Mock
    private PersonalConsultaService personalConsultaService;

    private PersonalController controller;

    @BeforeEach
    void setUp() {
        controller = new PersonalController(personalRepository, establecimientoRepository, personalConsultaService);
        when(establecimientoRepository.existsById(1L)).thenReturn(true);
        when(establecimientoRepository.existsById(999L)).thenReturn(false);

        when(personalRepository.save(any(Personal.class))).thenAnswer(invocation -> {
            Personal p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(50L);
            }
            return p;
        });
    }

    private PersonalInput crearInput(Long idEst, String nombre, BigDecimal costo, EstadoPersonal estado) {
        PersonalInput input = new PersonalInput();
        input.setIdEstablecimiento(idEst);
        input.setNombre(nombre);
        input.setEspecialidad("Medicina General");
        input.setCostoConsulta(costo);
        input.setDuracionEstandarMinutos(30);
        input.setEstado(estado);
        return input;
    }

    // ---------- CP-US58-01: Alta de Personal Válido (PE: Establecimiento Existente) ----------
    @Test
    @DisplayName("CP-US58-01 | Alta de personal con datos válidos retorna HTTP 201 Created")
    void registrarPersonal_datosValidos_retorna201() {
        PersonalInput input = crearInput(1L, "Dr. Carlos Gomez", new BigDecimal("1500.00"), EstadoPersonal.ACTIVO);

        ResponseEntity<Personal> respuesta = controller.crear(input);

        assertNotNull(respuesta);
        assertEquals(HttpStatus.CREATED, respuesta.getStatusCode());
        assertNotNull(respuesta.getBody());
        assertEquals(50L, respuesta.getBody().getId());
        assertEquals("Dr. Carlos Gomez", respuesta.getBody().getNombre());
        assertEquals(EstadoPersonal.ACTIVO, respuesta.getBody().getEstado());
        verify(personalRepository, times(1)).save(any(Personal.class));
    }

    // ---------- CP-US58-02: Alta de Personal con Establecimiento Inexistente ----------
    @Test
    @DisplayName("CP-US58-02 | Alta con establecimiento inexistente lanza IllegalArgumentException (400)")
    void registrarPersonal_establecimientoInexistente_lanza404() {
        PersonalInput input = crearInput(999L, "Dr. Fantasma", new BigDecimal("1200.00"), EstadoPersonal.ACTIVO);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> controller.crear(input));
        assertTrue(ex.getMessage().contains("establecimiento con id 999 no existe"));
        verify(personalRepository, never()).save(any());
    }

    // ---------- CP-US58-03: Consulta por ID Existente ----------
    @Test
    @DisplayName("CP-US58-03 | Consultar personal por ID existente retorna HTTP 200 OK")
    void consultarPersonal_idExistente_retorna200() {
        Personal p = new Personal();
        p.setId(50L);
        p.setNombre("Dra. Lucia Mendez");
        when(personalRepository.findById(50L)).thenReturn(Optional.of(p));

        ResponseEntity<Personal> respuesta = controller.obtener(50L);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals(50L, respuesta.getBody().getId());
        assertEquals("Dra. Lucia Mendez", respuesta.getBody().getNombre());
    }

    // ---------- CP-US58-04: Consulta por ID Inexistente ----------
    @Test
    @DisplayName("CP-US58-04 | Consultar personal por ID inexistente lanza ResourceNotFoundException (404)")
    void consultarPersonal_idInexistente_lanza404() {
        when(personalRepository.findById(888L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> controller.obtener(888L));
    }

    // ---------- CP-US58-05: Listado General de Personal ----------
    @Test
    @DisplayName("CP-US58-05 | Listar personal retorna HTTP 200 OK con colección")
    void listarPersonal_retornaColeccion() {
        Personal p1 = new Personal();
        p1.setId(1L);
        Personal p2 = new Personal();
        p2.setId(2L);
        when(personalRepository.findAll()).thenReturn(List.of(p1, p2));

        ResponseEntity<List<Personal>> respuesta = controller.listar();

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals(2, respuesta.getBody().size());
    }

    // ---------- CP-US58-06: Modificación de Personal Existente ----------
    @Test
    @DisplayName("CP-US58-06 | Actualizar personal existente retorna HTTP 200 OK con datos modificados")
    void actualizarPersonal_existente_retorna200() {
        Personal existente = new Personal();
        existente.setId(50L);
        existente.setNombre("Dr. Original");
        when(personalRepository.findById(50L)).thenReturn(Optional.of(existente));

        PersonalInput inputActualizado = crearInput(1L, "Dr. Actualizado", new BigDecimal("2000.00"), EstadoPersonal.ACTIVO);

        ResponseEntity<Personal> respuesta = controller.actualizar(50L, inputActualizado);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals("Dr. Actualizado", respuesta.getBody().getNombre());
        assertEquals(new BigDecimal("2000.00"), respuesta.getBody().getCostoConsulta());
        verify(personalRepository).save(existente);
    }

    // ---------- CP-US58-07: Modificación con Establecimiento Inexistente ----------
    @Test
    @DisplayName("CP-US58-07 | Actualizar personal con establecimiento inexistente lanza IllegalArgumentException (400)")
    void actualizarPersonal_establecimientoInexistente_lanza404() {
        Personal existente = new Personal();
        existente.setId(50L);
        when(personalRepository.findById(50L)).thenReturn(Optional.of(existente));

        PersonalInput input = crearInput(999L, "Dr. Modificado", new BigDecimal("1500.00"), EstadoPersonal.ACTIVO);

        assertThrows(IllegalArgumentException.class, () -> controller.actualizar(50L, input));
        verify(personalRepository, never()).save(any());
    }

    // ---------- CP-US58-08: Eliminación de Personal Existente ----------
    @Test
    @DisplayName("CP-US58-08 | Eliminar personal existente retorna HTTP 204 No Content")
    void eliminarPersonal_existente_retorna204() {
        when(personalRepository.existsById(50L)).thenReturn(true);

        ResponseEntity<Void> respuesta = controller.eliminar(50L);

        assertEquals(HttpStatus.NO_CONTENT, respuesta.getStatusCode());
        verify(personalRepository).deleteById(50L);
    }

    // ---------- CP-US58-09: Eliminación de Personal Inexistente ----------
    @Test
    @DisplayName("CP-US58-09 | Eliminar personal inexistente lanza ResourceNotFoundException (404)")
    void eliminarPersonal_inexistente_lanza404() {
        when(personalRepository.existsById(999L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> controller.eliminar(999L));
        verify(personalRepository, never()).deleteById(anyLong());
    }

    // ---------- CP-US58-10: Caja Negra - Partición de Equivalencia sobre Estado del Personal ----------
    @ParameterizedTest(name = "CP-US58-10 | PE: Alta de personal en estado {0} se persiste correctamente")
    @EnumSource(EstadoPersonal.class)
    @DisplayName("CP-US58-10 | PE: Estados válidos (ACTIVO, INACTIVO) son permitidos en el registro")
    void registrarPersonal_estadosValidos(EstadoPersonal estado) {
        PersonalInput input = crearInput(1L, "Dra. Test", new BigDecimal("1000.00"), estado);

        ResponseEntity<Personal> resp = controller.crear(input);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals(estado, resp.getBody().getEstado());
    }

    // ---------- CP-US58-11: Caja Negra - AVL en Costo de Consulta ----------
    @Test
    @DisplayName("CP-US58-11 | AVL: Costo consulta en frontera válida (0.01) es aceptado")
    void costoConsulta_fronteraValida_retorna201() {
        PersonalInput input = crearInput(1L, "Dr. Economico", new BigDecimal("0.01"), EstadoPersonal.ACTIVO);

        ResponseEntity<Personal> resp = controller.crear(input);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals(new BigDecimal("0.01"), resp.getBody().getCostoConsulta());
    }
}
