package com.laboratorio.turnos.api.funcionales;

import com.laboratorio.turnos.api.controller.ReservaController;
import com.laboratorio.turnos.api.dto.CrearReservaDTO;
import com.laboratorio.turnos.api.dto.mqtt.MqttReservaMessageDTO;
import com.laboratorio.turnos.api.exception.ResourceNotFoundException;
import com.laboratorio.turnos.api.model.Reserva;
import com.laboratorio.turnos.api.repository.ReservaRepository;
import com.laboratorio.turnos.api.service.MqttPublisherService;
import com.laboratorio.turnos.api.service.ReservaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * IS-70 / US-59 - Pruebas Reservas Completo
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("US-59 | Pruebas Reservas Completo (Caja Negra)")
public class ReservasCompletoFunctionalTest {

    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private MqttPublisherService mqttPublisherService;

    private ReservaService reservaService;
    private ReservaController controller;

    @BeforeEach
    void setUp() {
        reservaService = new ReservaService(reservaRepository, mqttPublisherService);
        controller = new ReservaController(reservaService);

        when(reservaRepository.save(any(Reserva.class))).thenAnswer(invocation -> {
            Reserva r = invocation.getArgument(0);
            if (r.getId() == null) {
                r.setId(77);
            }
            return r;
        });
    }

    private CrearReservaDTO crearDTO(Integer idPersonal, Integer idEstablecimiento, String email, LocalTime hora) {
        CrearReservaDTO dto = new CrearReservaDTO();
        dto.setIdPersonal(idPersonal);
        dto.setIdEstablecimiento(idEstablecimiento);
        dto.setEmailCliente(email);
        dto.setTelefonoCliente("099123456");
        dto.setFecha(LocalDate.now().plusDays(2));
        dto.setHora(hora);
        return dto;
    }

    // ---------- CP-US59-01: Generar Reserva Válida (201 Created + Evento MQTT) ----------
    @Test
    @DisplayName("CP-US59-01 | Generar reserva válida retorna HTTP 201 Created y estado SOLICITADO")
    void generarReserva_valida_retorna201YPublicaMqtt() {
        CrearReservaDTO dto = crearDTO(1, 1, "cliente@tapronto.com", LocalTime.of(10, 0));

        ResponseEntity<Map<String, Object>> respuesta = controller.generarReserva(dto);

        assertNotNull(respuesta);
        assertEquals(HttpStatus.CREATED, respuesta.getStatusCode());
        assertEquals("SOLICITADO", respuesta.getBody().get("status"));

        Reserva creada = (Reserva) respuesta.getBody().get("datos");
        assertNotNull(creada);
        assertEquals(77, creada.getId());
        assertEquals(30, creada.getDuracionMinutos()); // Duración fija por especificación
        assertEquals("SOLICITADO", creada.getEstado());

        ArgumentCaptor<MqttReservaMessageDTO> captor = ArgumentCaptor.forClass(MqttReservaMessageDTO.class);
        verify(mqttPublisherService, times(1)).publicarReserva(captor.capture());
        assertEquals("NUEVO", captor.getValue().getStatus());
        assertEquals(77, captor.getValue().getTurno().getId());
    }

    // ---------- CP-US59-02: Consultar Reserva por ID Existente ----------
    @Test
    @DisplayName("CP-US59-02 | Consultar reserva por ID existente retorna HTTP 200 OK con datos")
    void consultarReserva_idExistente_retorna200() {
        Reserva r = new Reserva();
        r.setId(77);
        r.setEstado("Agendado");
        when(reservaRepository.findById(77)).thenReturn(Optional.of(r));

        ResponseEntity<?> respuesta = controller.buscarPorId(77);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertTrue(respuesta.getBody() instanceof Reserva);
        assertEquals(77, ((Reserva) respuesta.getBody()).getId());
    }

    // ---------- CP-US59-03: Consultar Reserva por ID Inexistente ----------
    @Test
    @DisplayName("CP-US59-03 | Consultar reserva por ID inexistente retorna HTTP 404 Not Found")
    void consultarReserva_idInexistente_retorna404() {
        when(reservaRepository.findById(999)).thenReturn(Optional.empty());

        ResponseEntity<?> respuesta = controller.buscarPorId(999);

        assertEquals(HttpStatus.NOT_FOUND, respuesta.getStatusCode());
    }

    // ---------- CP-US59-04: Listado General y con Filtro ----------
    @Test
    @DisplayName("CP-US59-04 | Listar reservas permite consulta global y filtrado por profesional")
    void listarReservas_generalYFiltro_retornaColeccion() {
        Reserva r1 = new Reserva();
        r1.setId(1);
        r1.setIdPersonal(1);
        Reserva r2 = new Reserva();
        r2.setId(2);
        r2.setIdPersonal(2);

        when(reservaRepository.findAll()).thenReturn(List.of(r1, r2));
        when(reservaRepository.findByIdPersonal(1)).thenReturn(List.of(r1));

        // Listado general
        ResponseEntity<List<Reserva>> general = controller.listarReservas(null);
        assertEquals(2, general.getBody().size());

        // Filtrado por idPersonal = 1
        ResponseEntity<List<Reserva>> filtrado = controller.listarReservas(1);
        assertEquals(1, filtrado.getBody().size());
        assertEquals(1, filtrado.getBody().get(0).getIdPersonal());
    }

    // ---------- CP-US59-05: Modificar Reserva Existente (202 Accepted + Reenvío a Validación) ----------
    @Test
    @DisplayName("CP-US59-05 | Actualizar reserva existente retorna HTTP 202 Accepted y re-publica evento ACTUALIZAR")
    void actualizarReserva_existente_retorna202() {
        Reserva previa = new Reserva();
        previa.setId(77);
        previa.setEstado("Agendado");
        when(reservaRepository.findById(77)).thenReturn(Optional.of(previa));

        CrearReservaDTO nuevoDto = crearDTO(1, 1, "cambio@tapronto.com", LocalTime.of(15, 0));

        ResponseEntity<Map<String, Object>> respuesta = controller.actualizarReserva(77, nuevoDto);

        assertEquals(HttpStatus.ACCEPTED, respuesta.getStatusCode());
        assertEquals("SOLICITADO", respuesta.getBody().get("status"));

        ArgumentCaptor<MqttReservaMessageDTO> captor = ArgumentCaptor.forClass(MqttReservaMessageDTO.class);
        verify(mqttPublisherService).publicarReserva(captor.capture());
        assertEquals("ACTUALIZAR", captor.getValue().getStatus());
    }

    // ---------- CP-US59-06: Modificar Reserva Inexistente ----------
    @Test
    @DisplayName("CP-US59-06 | Actualizar reserva inexistente lanza ResourceNotFoundException (404)")
    void actualizarReserva_inexistente_lanza404() {
        when(reservaRepository.findById(999)).thenReturn(Optional.empty());
        CrearReservaDTO dto = crearDTO(1, 1, "test@correo.com", LocalTime.of(10, 0));

        assertThrows(ResourceNotFoundException.class, () -> controller.actualizarReserva(999, dto));
    }
}
