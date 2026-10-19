package com.laboratorio.turnos.api.reglas;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * US-52 - Reglas de negocio de ReservaService, de forma aislada:
 * repositorio y publicador MQTT son mocks (sin base de datos ni broker).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("US-52 | Reglas de negocio: ReservaService")
class ReservaServiceReglasNegocioTest {

    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private MqttPublisherService mqttPublisherService;

    private ReservaService service;

    @BeforeEach
    void setUp() {
        service = new ReservaService(reservaRepository, mqttPublisherService);
        // el repositorio devuelve la misma entidad y simula el id autogenerado
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> {
            Reserva r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(77);
            }
            return r;
        });
    }

    private CrearReservaDTO dto() {
        CrearReservaDTO dto = new CrearReservaDTO();
        dto.setIdPersonal(3);
        dto.setIdEstablecimiento(2);
        dto.setEmailCliente("cliente@correo.com");
        dto.setTelefonoCliente("099123456");
        dto.setFecha(LocalDate.now().plusDays(2));
        dto.setHora(LocalTime.of(10, 0));
        return dto;
    }

    private Reserva reservaEnEstado(String estado) {
        Reserva r = new Reserva();
        r.setId(10);
        r.setIdPersonal(3);
        r.setEmailSolicitante("cliente@correo.com");
        r.setTelefonoSolicitante("099123456");
        r.setFechaTurno(LocalDate.now().plusDays(2));
        r.setHoraTurno(LocalTime.of(10, 0));
        r.setEstado(estado);
        return r;
    }

    // ---------- duracion fija de 30 minutos ----------

    @Test
    @DisplayName("Toda reserva nueva dura 30 minutos")
    void reservaNueva_duraTreintaMinutos() {
        Reserva guardada = service.generarReserva(dto());
        assertEquals(30, guardada.getDuracionMinutos());
    }

    @Test
    @DisplayName("La duracion es 30 minutos para cualquier hora solicitada")
    void duracionIndependienteDeLaHora() {
        CrearReservaDTO d = dto();
        d.setHora(LocalTime.of(16, 30));
        assertEquals(30, service.generarReserva(d).getDuracionMinutos());
    }

    // ---------- alta de reserva ----------

    @Test
    @DisplayName("Toda reserva nueva se registra en estado SOLICITADO")
    void reservaNueva_quedaSolicitada() {
        Reserva guardada = service.generarReserva(dto());
        assertEquals("SOLICITADO", guardada.getEstado());
        assertNotNull(guardada.getFechaRegistro());
    }

    @Test
    @DisplayName("La reserva copia los datos del DTO (profesional, establecimiento, cliente, fecha y hora)")
    void reservaNueva_copiaDatosDelDto() {
        CrearReservaDTO d = dto();
        Reserva guardada = service.generarReserva(d);

        assertEquals(3, guardada.getIdPersonal());
        assertEquals(2, guardada.getIdEstablecimiento());
        assertEquals("cliente@correo.com", guardada.getEmailSolicitante());
        assertEquals("099123456", guardada.getTelefonoSolicitante());
        assertEquals(d.getFecha(), guardada.getFechaTurno());
        assertEquals(LocalTime.of(10, 0), guardada.getHoraTurno());
    }

    @Test
    @DisplayName("Al crear se publica un evento NUEVO con el id generado, para que el validador decida")
    void reservaNueva_publicaEventoNuevo() {
        service.generarReserva(dto());

        ArgumentCaptor<MqttReservaMessageDTO> captor = ArgumentCaptor.forClass(MqttReservaMessageDTO.class);
        verify(mqttPublisherService, times(1)).publicarReserva(captor.capture());

        MqttReservaMessageDTO msg = captor.getValue();
        assertEquals("NUEVO", msg.getStatus());
        assertEquals(77, msg.getTurno().getId());
        assertEquals(3, msg.getTurno().getIdPersonal());
        assertEquals(2, msg.getTurno().getIdEstablecimiento());
    }

    @Test
    @DisplayName("La API no valida disponibilidad: delega en el validador asincrono (no rechaza al crear)")
    void reservaNueva_noRechazaPorHorarioOcupado() {
        // dos solicitudes identicas: ambas quedan SOLICITADO; el validador resolvera el choque
        Reserva r1 = service.generarReserva(dto());
        Reserva r2 = service.generarReserva(dto());
        assertEquals("SOLICITADO", r1.getEstado());
        assertEquals("SOLICITADO", r2.getEstado());
        verify(mqttPublisherService, times(2)).publicarReserva(any());
    }

    // ---------- modificacion ----------

    @Test
    @DisplayName("Modificar una reserva existente la devuelve a SOLICITADO y publica ACTUALIZAR")
    void actualizar_vuelveASolicitadoYPublica() {
        when(reservaRepository.findById(10)).thenReturn(Optional.of(reservaEnEstado("Agendado")));

        Reserva r = service.actualizarReserva(10, dto());

        assertEquals("SOLICITADO", r.getEstado());
        ArgumentCaptor<MqttReservaMessageDTO> captor = ArgumentCaptor.forClass(MqttReservaMessageDTO.class);
        verify(mqttPublisherService).publicarReserva(captor.capture());
        assertEquals("ACTUALIZAR", captor.getValue().getStatus());
        assertEquals(10, captor.getValue().getTurno().getId());
    }

    @Test
    @DisplayName("Modificar una reserva inexistente falla con ResourceNotFoundException y no publica")
    void actualizar_inexistente() {
        when(reservaRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.actualizarReserva(999, dto()));
        verify(reservaRepository, never()).save(any());
        verifyNoInteractions(mqttPublisherService);
    }

    @ParameterizedTest(name = "No se puede modificar una reserva en estado {0}")
    @ValueSource(strings = {"Atendido", "Facturado", "atendido", "FACTURADO"})
    void actualizar_atendidaOFacturada(String estado) {
        when(reservaRepository.findById(10)).thenReturn(Optional.of(reservaEnEstado(estado)));

        assertThrows(IllegalArgumentException.class, () -> service.actualizarReserva(10, dto()));
        verify(reservaRepository, never()).save(any());
        verifyNoInteractions(mqttPublisherService);
    }

    // ---------- eliminacion ----------

    @Test
    @DisplayName("Eliminar una reserva inexistente devuelve false y no borra nada")
    void eliminar_inexistente() {
        when(reservaRepository.findById(999)).thenReturn(Optional.empty());

        assertFalse(service.eliminarReserva(999));
        verify(reservaRepository, never()).delete(any());
    }

    @ParameterizedTest(name = "No se puede eliminar una reserva en estado {0}")
    @ValueSource(strings = {"Atendido", "Facturado"})
    void eliminar_atendidaOFacturada(String estado) {
        when(reservaRepository.findById(10)).thenReturn(Optional.of(reservaEnEstado(estado)));

        assertThrows(IllegalArgumentException.class, () -> service.eliminarReserva(10));
        verify(reservaRepository, never()).delete(any());
    }

    @ParameterizedTest(name = "Se puede eliminar una reserva en estado {0}")
    @ValueSource(strings = {"SOLICITADO", "Agendado", "Rechazado/Turno Ocupado"})
    void eliminar_estadosPermitidos(String estado) {
        Reserva r = reservaEnEstado(estado);
        when(reservaRepository.findById(10)).thenReturn(Optional.of(r));

        assertTrue(service.eliminarReserva(10));
        verify(reservaRepository).delete(r);
    }
}
