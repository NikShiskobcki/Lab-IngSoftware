package com.laboratorio.turnos.api.funcionales;

import com.laboratorio.turnos.api.establecimiento.Establecimiento;
import com.laboratorio.turnos.api.establecimiento.EstablecimientoController;
import com.laboratorio.turnos.api.establecimiento.EstablecimientoRepository;
import com.laboratorio.turnos.api.establecimiento.dto.EstablecimientoInput;
import com.laboratorio.turnos.api.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * IS-68 / US-57 - Pruebas Establecimiento completo
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("US-57 | Pruebas Establecimiento completo")
public class EstablecimientoCompletoFunctionalTest {

    @Mock
    private EstablecimientoRepository establecimientoRepository;

    private EstablecimientoController controller;

    @BeforeEach
    void setUp() {
        controller = new EstablecimientoController(establecimientoRepository);
        when(establecimientoRepository.save(any(Establecimiento.class))).thenAnswer(invocation -> {
            Establecimiento e = invocation.getArgument(0);
            if (e.getId() == null) {
                e.setId(100L);
            }
            return e;
        });
    }

    private EstablecimientoInput crearInput(String nombre, LocalTime apertura, LocalTime cierre) {
        EstablecimientoInput input = new EstablecimientoInput();
        input.setNombreComercial(nombre);
        input.setDireccion("Av. 18 de Julio 1234");
        input.setTelefono("29001234");
        input.setCorreoElectronico("contacto@tapronto.com");
        input.setHorarioApertura(apertura);
        input.setHorarioCierre(cierre);
        return input;
    }

    // ---------- CP-US57-01: Alta de Establecimiento Válido (Clase Válida) ----------
    @Test
    @DisplayName("CP-US57-01 | Alta de establecimiento con datos válidos retorna HTTP 201 Created")
    void registrarEstablecimiento() {
        EstablecimientoInput input = crearInput("TaPronto Central", LocalTime.of(8, 0), LocalTime.of(20, 0));

        ResponseEntity<Establecimiento> respuesta = controller.crear(input);

        assertNotNull(respuesta);
        assertEquals(HttpStatus.CREATED, respuesta.getStatusCode());
        assertNotNull(respuesta.getBody());
        assertEquals(100L, respuesta.getBody().getId());
        assertEquals("TaPronto Central", respuesta.getBody().getNombreComercial());
        verify(establecimientoRepository, times(1)).save(any(Establecimiento.class));
    }

    // ---------- CP-US57-02: Consulta por ID Existente (ID Existente) ----------
    @Test
    @DisplayName("CP-US57-02 | Consultar establecimiento por ID existente retorna HTTP 200 OK")
    void consultarEstablecimientoExistente() {
        Establecimiento existente = new Establecimiento();
        existente.setId(100L);
        existente.setNombreComercial("TaPronto Central");
        when(establecimientoRepository.findById(100L)).thenReturn(Optional.of(existente));

        ResponseEntity<Establecimiento> respuesta = controller.obtener(100L);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals(100L, respuesta.getBody().getId());
        assertEquals("TaPronto Central", respuesta.getBody().getNombreComercial());
    }

    // ---------- CP-US57-03: Consulta por ID Inexistente (ID No Existente) ----------
    @Test
    @DisplayName("CP-US57-03 | Consultar establecimiento por ID inexistente lanza ResourceNotFoundException (404)")
    void consultarEstablecimientoInexistente() {
        when(establecimientoRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> controller.obtener(999L));
        assertTrue(ex.getMessage().contains("999"));
    }

    // ---------- CP-US57-04: Listado General de Establecimientos ----------
    @Test
    @DisplayName("CP-US57-04 | Listar establecimientos retorna HTTP 200 OK con colección")
    void listarEstablecimientos() {
        Establecimiento e1 = new Establecimiento();
        e1.setId(1L);
        Establecimiento e2 = new Establecimiento();
        e2.setId(2L);
        when(establecimientoRepository.findAll()).thenReturn(List.of(e1, e2));

        ResponseEntity<List<Establecimiento>> respuesta = controller.listar();

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals(2, respuesta.getBody().size());
    }

    // ---------- CP-US57-05: Modificación de Establecimiento Existente ----------
    @Test
    @DisplayName("CP-US57-05 | Actualizar establecimiento existente retorna HTTP 200 OK con datos actualizados")
    void actualizarEstablecimiento() {
        Establecimiento existente = new Establecimiento();
        existente.setId(100L);
        existente.setNombreComercial("TaPronto Antiguo");
        when(establecimientoRepository.findById(100L)).thenReturn(Optional.of(existente));

        EstablecimientoInput inputActualizado = crearInput("TaPronto Renovado", LocalTime.of(9, 0), LocalTime.of(19, 0));

        ResponseEntity<Establecimiento> respuesta = controller.actualizar(100L, inputActualizado);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals("TaPronto Renovado", respuesta.getBody().getNombreComercial());
        assertEquals(LocalTime.of(9, 0), respuesta.getBody().getHorarioApertura());
        verify(establecimientoRepository).save(existente);
    }

    // ---------- CP-US57-06: Modificación de Establecimiento Inexistente ----------
    @Test
    @DisplayName("CP-US57-06 | Actualizar establecimiento inexistente lanza ResourceNotFoundException (404)")
    void actualizarEstablecimientoInexistente() {
        when(establecimientoRepository.findById(999L)).thenReturn(Optional.empty());
        EstablecimientoInput input = crearInput("Nombre", LocalTime.of(8, 0), LocalTime.of(18, 0));

        assertThrows(ResourceNotFoundException.class, () -> controller.actualizar(999L, input));
    }

    // ---------- CP-US57-07: Eliminación de Establecimiento Existente ----------
    @Test
    @DisplayName("CP-US57-07 | Eliminar establecimiento existente retorna HTTP 204 No Content")
    void eliminarEstablecimiento() {
        when(establecimientoRepository.existsById(100L)).thenReturn(true);

        ResponseEntity<Void> respuesta = controller.eliminar(100L);

        assertEquals(HttpStatus.NO_CONTENT, respuesta.getStatusCode());
        verify(establecimientoRepository).deleteById(100L);
    }

    // ---------- CP-US57-08: Eliminación de Establecimiento Inexistente ----------
    @Test
    @DisplayName("CP-US57-08 | Eliminar establecimiento inexistente lanza ResourceNotFoundException (404)")
    void eliminarEstablecimientoInexistente() {
        when(establecimientoRepository.existsById(999L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> controller.eliminar(999L));
        verify(establecimientoRepository, never()).deleteById(anyLong());
    }

    // ---------- CP-US57-09: Horarios de Apertura y Cierre ----------
    @Test
    @DisplayName("CP-US57-09 | AVL: Cierre anterior o igual a la apertura es rechazado con IllegalArgumentException")
    void horarioCierreAnteriorOIgual() {
        // Límite: Apertura = Cierre (08:00 y 08:00)
        EstablecimientoInput inputIgual = crearInput("Test", LocalTime.of(8, 0), LocalTime.of(8, 0));
        assertThrows(IllegalArgumentException.class, () -> controller.crear(inputIgual));

        // Partición inválida: Cierre anterior a apertura (08:00 apertura, 07:00 cierre)
        EstablecimientoInput inputAnterior = crearInput("Test", LocalTime.of(8, 0), LocalTime.of(7, 0));
        assertThrows(IllegalArgumentException.class, () -> controller.crear(inputAnterior));

        verify(establecimientoRepository, never()).save(any());
    }

    @ParameterizedTest(name = "CP-US57-10 | AVL Horarios extremos: apertura={0}, cierre={1}")
    @CsvSource({
            "00:00, 23:59", // Jornada completa límite
            "08:00, 08:01", // Diferencia mínima de 1 minuto
            "06:30, 22:00"  // Horario extendido normal
    })
    @DisplayName("CP-US57-10 | AVL: Horarios en fronteras válidas son aceptados (201)")
    void horariosFronteraValidos(String aperturaStr, String cierreStr) {
        LocalTime ap = LocalTime.parse(aperturaStr);
        LocalTime ci = LocalTime.parse(cierreStr);
        EstablecimientoInput input = crearInput("TaPronto Frontera", ap, ci);

        ResponseEntity<Establecimiento> resp = controller.crear(input);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals(ap, resp.getBody().getHorarioApertura());
        assertEquals(ci, resp.getBody().getHorarioCierre());
    }
}
