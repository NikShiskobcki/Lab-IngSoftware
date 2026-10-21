package com.laboratorio.turnos.api.funcionales;

import com.laboratorio.turnos.api.controller.ReservaController;
import com.laboratorio.turnos.api.dto.CrearReservaDTO;
import com.laboratorio.turnos.api.model.Reserva;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * IS-73 / US-62 - Prueba doble reserva (Evitar doble reserva)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("US-62 | Prueba doble reserva (Nivel API)")
public class DobleReservaFunctionalTest {

    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private MqttPublisherService mqttPublisherService;

    private ReservaService service;
    private ReservaController controller;

    private final AtomicInteger idGen = new AtomicInteger(100);

    @BeforeEach
    void setUp() {
        service = new ReservaService(reservaRepository, mqttPublisherService);
        controller = new ReservaController(service);

        when(reservaRepository.save(any(Reserva.class))).thenAnswer(invocation -> {
            Reserva r = invocation.getArgument(0);
            if (r.getId() == null) {
                r.setId(idGen.incrementAndGet());
            }
            return r;
        });
    }

    @Test
    @DisplayName("CP-US62-API-01 | Dos solicitudes para el mismo slot generan eventos MQTT independientes con ID único")
    void solicitudesConcurrentes_generanEventosMqttConIdUnico() {
        LocalDate fecha = LocalDate.now().plusDays(3);
        LocalTime hora = LocalTime.of(10, 0);

        CrearReservaDTO dto1 = new CrearReservaDTO();
        dto1.setIdPersonal(1);
        dto1.setIdEstablecimiento(1);
        dto1.setEmailCliente("cliente1@correo.com");
        dto1.setTelefonoCliente("099111222");
        dto1.setFecha(fecha);
        dto1.setHora(hora);

        CrearReservaDTO dto2 = new CrearReservaDTO();
        dto2.setIdPersonal(1);
        dto2.setIdEstablecimiento(1);
        dto2.setEmailCliente("cliente2@correo.com");
        dto2.setTelefonoCliente("099333444");
        dto2.setFecha(fecha);
        dto2.setHora(hora);

        ResponseEntity<Map<String, Object>> resp1 = controller.generarReserva(dto1);
        ResponseEntity<Map<String, Object>> resp2 = controller.generarReserva(dto2);

        assertEquals(HttpStatus.CREATED, resp1.getStatusCode());
        assertEquals(HttpStatus.CREATED, resp2.getStatusCode());

        Reserva r1 = (Reserva) resp1.getBody().get("datos");
        Reserva r2 = (Reserva) resp2.getBody().get("datos");

        assertNotEquals(r1.getId(), r2.getId(), "Cada solicitud debe recibir un identificador único antes de ser procesada por el validador");
        verify(mqttPublisherService, times(2)).publicarReserva(any());
    }
}
