package com.laboratorio.turnos.validador.service;

import com.laboratorio.turnos.validador.model.ReservaTurno;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * US-53 - Transiciones de estado que dispara el servicio validador (ciclo de 1 minuto):
 *   Solicitado -> Agendado
 *   Solicitado -> Rechazado/Solicitud No Valida
 *   Solicitado -> Rechazado/Turno Ocupado
 *   Agendado   -> Atendido   (y publicacion MQTT en turnos/atendidos)
 */
@DisplayName("US-53 | Transiciones de estado: servicio validador (cada 1 minuto)")
@MockitoSettings(strictness = Strictness.LENIENT)
class TurnoValidadorTransicionesEstadoTest extends TurnoValidadorTestBase {

    // ---------- Solicitado -> ... ----------

    @Test
    @DisplayName("Solicitado -> Agendado cuando la solicitud es valida y el horario esta libre")
    void solicitadoAAgendado() {
        ReservaTurno t = recibir(turno(1, futuro(), LocalTime.of(10, 0), "SOLICITADO"));

        service.revisarTurnosNuevos();

        assertEquals("Agendado", t.getEstado());
        verify(reservaRepo).save(t);
    }

    @Test
    @DisplayName("Solicitado -> Rechazado/Solicitud No Valida cuando el profesional no existe")
    void solicitadoARechazadoSolicitudNoValida() {
        ReservaTurno t = recibir(turno(1, futuro(), LocalTime.of(10, 0), "SOLICITADO"));
        when(personalRepo.findById(ID_PERSONAL)).thenReturn(Optional.empty());

        service.revisarTurnosNuevos();

        assertEquals("Rechazado/Solicitud No Valida", t.getEstado());
        verify(reservaRepo).save(t);
    }

    @Test
    @DisplayName("Solicitado -> Rechazado/Turno Ocupado cuando el horario ya esta tomado")
    void solicitadoARechazadoTurnoOcupado() {
        LocalDate dia = futuro();
        turnosExistentes(dia, turno(9, dia, LocalTime.of(10, 0), "Agendado"));
        ReservaTurno t = recibir(turno(1, dia, LocalTime.of(10, 0), "SOLICITADO"));

        service.revisarTurnosNuevos();

        assertEquals("Rechazado/Turno Ocupado", t.getEstado());
        verify(reservaRepo).save(t);
    }

    @Test
    @DisplayName("Un rechazo no dispara ninguna publicacion MQTT")
    void rechazoNoPublicaEnMqtt() throws Exception {
        recibir(turno(1, LocalDate.now().minusDays(1), LocalTime.of(10, 0), "SOLICITADO"));

        service.revisarTurnosNuevos();
        commit();

        verify(mqtt, never()).publicarTurnoAtendido(any());
    }

    @Test
    @DisplayName("En un mismo ciclo, dos solicitudes al mismo horario: la primera se agenda y la segunda queda Ocupado")
    void dosSolicitudesAlMismoHorarioEnUnCiclo() {
        LocalDate dia = futuro();
        ReservaTurno primera = recibir(turno(1, dia, LocalTime.of(10, 0), "SOLICITADO"));
        ReservaTurno segunda = recibir(turno(2, dia, LocalTime.of(10, 0), "SOLICITADO"));
        // el repositorio refleja lo ya procesado en el ciclo (ambas estan en la lista del dia)
        turnosExistentes(dia, primera, segunda);

        service.revisarTurnosNuevos();

        assertEquals("Agendado", primera.getEstado());
        assertEquals("Rechazado/Turno Ocupado", segunda.getEstado());
    }

    // ---------- Agendado -> Atendido ----------

    @Test
    @DisplayName("Agendado -> Atendido cuando la fecha y hora del turno ya pasaron")
    void agendadoAAtendido() {
        ReservaTurno t = turno(7, LocalDate.now().minusDays(1), LocalTime.of(10, 0), "Agendado");
        when(reservaRepo.findByEstadoIn(List.of("Agendado"))).thenReturn(List.of(t));

        service.revisarTurnosNuevos();

        assertEquals("Atendido", t.getEstado());
        verify(reservaRepo).save(t);
    }

    @Test
    @DisplayName("Agendado -> Atendido: el turno de hoy cuya hora paso hace unos minutos tambien pasa a Atendido")
    void agendadoDeHoyConHoraPasada() {
        LocalDateTime haceUnRato = LocalDateTime.now().minusMinutes(5);
        ReservaTurno t = turno(7, haceUnRato.toLocalDate(), haceUnRato.toLocalTime(), "Agendado");
        when(reservaRepo.findByEstadoIn(List.of("Agendado"))).thenReturn(List.of(t));

        service.revisarTurnosNuevos();

        assertEquals("Atendido", t.getEstado());
    }

    @Test
    @DisplayName("Un turno Agendado futuro NO cambia de estado ni se publica")
    void agendadoFuturoSeMantiene() throws Exception {
        ReservaTurno t = turno(7, futuro(), LocalTime.of(10, 0), "Agendado");
        when(reservaRepo.findByEstadoIn(List.of("Agendado"))).thenReturn(List.of(t));

        service.revisarTurnosNuevos();
        commit();

        assertEquals("Agendado", t.getEstado());
        verify(reservaRepo, never()).save(any(ReservaTurno.class));
        verify(mqtt, never()).publicarTurnoAtendido(any());
    }

    @Test
    @DisplayName("El evento turnos/atendidos se publica solo despues del commit, no antes")
    void publicaSoloDespuesDelCommit() throws Exception {
        ReservaTurno t = turno(7, LocalDate.now().minusDays(1), LocalTime.of(10, 0), "Agendado");
        when(reservaRepo.findByEstadoIn(List.of("Agendado"))).thenReturn(List.of(t));

        service.revisarTurnosNuevos();
        verifyNoInteractions(mqtt);     // todavia no hubo commit

        commit();
        verify(mqtt, times(1)).publicarTurnoAtendido(7);
    }

    @Test
    @DisplayName("Con varios turnos vencidos se publica un evento por cada uno y solo por los vencidos")
    void variosTurnosVencidos() throws Exception {
        ReservaTurno vencido1 = turno(7, LocalDate.now().minusDays(2), LocalTime.of(9, 0), "Agendado");
        ReservaTurno vencido2 = turno(8, LocalDate.now().minusDays(1), LocalTime.of(11, 0), "Agendado");
        ReservaTurno futuro = turno(9, futuro(), LocalTime.of(11, 0), "Agendado");
        when(reservaRepo.findByEstadoIn(List.of("Agendado"))).thenReturn(List.of(vencido1, vencido2, futuro));

        service.revisarTurnosNuevos();
        commit();

        assertEquals("Atendido", vencido1.getEstado());
        assertEquals("Atendido", vencido2.getEstado());
        assertEquals("Agendado", futuro.getEstado());
        verify(mqtt).publicarTurnoAtendido(7);
        verify(mqtt).publicarTurnoAtendido(8);
        verify(mqtt, never()).publicarTurnoAtendido(9);
    }

    @Test
    @DisplayName("Si falla la publicacion MQTT, el turno queda Atendido y el error no se propaga")
    void falloDePublicacionNoRevierteElEstado() throws Exception {
        ReservaTurno t = turno(7, LocalDate.now().minusDays(1), LocalTime.of(10, 0), "Agendado");
        when(reservaRepo.findByEstadoIn(List.of("Agendado"))).thenReturn(List.of(t));
        doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED))
                .when(mqtt).publicarTurnoAtendido(7);

        service.revisarTurnosNuevos();

        assertDoesNotThrow(this::commit);
        assertEquals("Atendido", t.getEstado());
    }

    @Test
    @DisplayName("Un turno que no esta Agendado (p.ej. Facturado) nunca se vuelve a marcar Atendido")
    void soloLosAgendadosPasanAAtendido() {
        // el servicio consulta unicamente el estado "Agendado"
        service.revisarTurnosNuevos();

        verify(reservaRepo).findByEstadoIn(List.of("Agendado"));
    }

    // ---------- robustez del ciclo ----------

    @Test
    @DisplayName("Un ciclo sin eventos pendientes termina sin errores ni escrituras")
    void cicloSinEventos() {
        assertDoesNotThrow(() -> service.revisarTurnosNuevos());
        verify(reservaRepo, never()).save(any(ReservaTurno.class));
    }

    @Test
    @DisplayName("Si el ciclo falla, los eventos se devuelven a la cola para reintentarse y se propaga el error")
    void falloDelCicloReencolaLosEventos() {
        recibir(turno(4, futuro(), LocalTime.of(10, 0), "SOLICITADO"));
        cola.agregar(5);
        when(reservaRepo.findById(5)).thenThrow(new RuntimeException("base de datos caida"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.revisarTurnosNuevos());

        assertNotNull(ex.getCause());
        assertEquals(4, cola.siguiente());
        assertEquals(5, cola.siguiente());
        assertNull(cola.siguiente());
    }

    // ---------- frecuencia del ciclo: cada 1 minuto ----------

    @Test
    @DisplayName("El ciclo de validacion esta programado cada 60000 ms (1 minuto)")
    void cicloProgramadoCadaUnMinuto() throws Exception {
        Method m = TurnoValidadorService.class.getMethod("revisarTurnosNuevos");
        Scheduled s = m.getAnnotation(Scheduled.class);

        assertNotNull(s, "revisarTurnosNuevos debe estar anotado con @Scheduled");
        assertTrue(s.fixedRateString().endsWith(":60000}"),
                "el valor por defecto del intervalo debe ser 60000 ms, pero es " + s.fixedRateString());
    }

    @Test
    @DisplayName("application.yml configura validador.intervalo-ms en 60000")
    void configuracionDelIntervalo() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            assertNotNull(in, "no se encontro application.yml");
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("intervalo-ms: 60000"));
        }
    }
}
