package com.laboratorio.turnos.api.integracion;

import com.laboratorio.turnos.api.dto.CrearReservaDTO;
import com.laboratorio.turnos.api.dto.mqtt.MqttReservaMessageDTO;
import com.laboratorio.turnos.api.exception.ResourceNotFoundException;
import com.laboratorio.turnos.api.model.Reserva;
import com.laboratorio.turnos.api.service.MqttPublisherService;
import com.laboratorio.turnos.api.service.ReservaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Import(ReservaService.class)
@DisplayName("ReservaService con repositorio real y MariaDB (MQTT simulado)")
class ReservaServiceIntegracionTest extends IntegracionJpaBaseTest {

    @MockBean MqttPublisherService mqtt;   // broker mockeado
    @Autowired ReservaService servicio;

    @BeforeEach
    void datosBase() {
        jdbc.update("INSERT INTO establecimientos (id, nombre_comercial, direccion, telefono, "
                + "correo_electronico, horario_apertura, horario_cierre) "
                + "VALUES (1, 'Clinica Test', 'Calle 1', '099111111', 'a@b.com', '08:00', '18:00')");
        jdbc.update("INSERT INTO personal (id, id_establecimiento, nombre, especialidad, costo_consulta) "
                + "VALUES (1, 1, 'Dra. Ana Perez', 'Clinica', 1500.00)");
    }

    private CrearReservaDTO dto(int idPersonal, String hora) {
        CrearReservaDTO d = new CrearReservaDTO();
        d.setIdPersonal(idPersonal);
        d.setIdEstablecimiento(1);
        d.setEmailCliente("cliente@test.com");
        d.setTelefonoCliente("099123456");
        d.setFecha(LocalDate.of(2030, 1, 1));
        d.setHora(LocalTime.parse(hora));
        return d;
    }

    private void forzarEstado(int id, String estado) {
        em.flush();
        jdbc.update("UPDATE reservas_turnos SET estado = ? WHERE id = ?", estado, id);
        em.clear();
    }

    @Test
    @DisplayName("Generar una reserva la guarda SOLICITADO y publica un mensaje NUEVO con su id")
    void generarReservaGuardaYPublica() {
        Reserva r = servicio.generarReserva(dto(1, "10:00"));
        em.flush();

        assertThat(r.getId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT estado FROM reservas_turnos WHERE id = ?", String.class, r.getId()))
                .isEqualTo("SOLICITADO");
        assertThat(jdbc.queryForObject("SELECT email_solicitante FROM reservas_turnos WHERE id = ?", String.class, r.getId()))
                .isEqualTo("cliente@test.com");

        ArgumentCaptor<MqttReservaMessageDTO> msg = ArgumentCaptor.forClass(MqttReservaMessageDTO.class);
        verify(mqtt).publicarReserva(msg.capture());
        assertThat(msg.getValue().getStatus()).isEqualTo("NUEVO");
        assertThat(msg.getValue().getTurno().getId()).isEqualTo(r.getId());
        assertThat(msg.getValue().getTurno().getIdPersonal()).isEqualTo(1);
    }

    @Test
    @DisplayName("Actualizar una reserva cambia sus datos, vuelve a SOLICITADO y publica ACTUALIZAR")
    void actualizarReserva() {
        Reserva original = servicio.generarReserva(dto(1, "10:00"));
        forzarEstado(original.getId(), "Agendado");

        servicio.actualizarReserva(original.getId(), dto(1, "11:30"));
        em.flush();

        assertThat(jdbc.queryForObject("SELECT TIME_FORMAT(hora_turno, '%H:%i') FROM reservas_turnos WHERE id = ?",
                String.class, original.getId())).isEqualTo("11:30");
        assertThat(jdbc.queryForObject("SELECT estado FROM reservas_turnos WHERE id = ?", String.class, original.getId()))
                .isEqualTo("SOLICITADO");

        ArgumentCaptor<MqttReservaMessageDTO> msg = ArgumentCaptor.forClass(MqttReservaMessageDTO.class);
        verify(mqtt, org.mockito.Mockito.times(2)).publicarReserva(msg.capture());
        assertThat(msg.getValue().getStatus()).isEqualTo("ACTUALIZAR");
    }

    @Test
    @DisplayName("No se puede modificar una reserva Atendido ni Facturado")
    void noSeModificaAtendidaNiFacturada() {
        Reserva r = servicio.generarReserva(dto(1, "10:00"));
        for (String estado : new String[]{"Atendido", "Facturado"}) {
            forzarEstado(r.getId(), estado);
            assertThrows(IllegalArgumentException.class, () -> servicio.actualizarReserva(r.getId(), dto(1, "12:00")));
            assertThat(jdbc.queryForObject("SELECT TIME_FORMAT(hora_turno, '%H:%i') FROM reservas_turnos WHERE id = ?",
                    String.class, r.getId())).isEqualTo("10:00");
        }
    }

    @Test
    @DisplayName("Actualizar una reserva inexistente lanza ResourceNotFoundException y no publica nada")
    void actualizarInexistente() {
        assertThrows(ResourceNotFoundException.class, () -> servicio.actualizarReserva(999, dto(1, "10:00")));
        verify(mqtt, never()).publicarReserva(any());
    }

    @Test
    @DisplayName("Eliminar una reserva Agendado la borra de la base")
    void eliminarReservaAgendada() {
        Reserva r = servicio.generarReserva(dto(1, "10:00"));
        forzarEstado(r.getId(), "Agendado");

        assertThat(servicio.eliminarReserva(r.getId())).isTrue();
        em.flush();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservas_turnos", Integer.class)).isEqualTo(0);
    }

    @Test
    @DisplayName("No se puede eliminar una reserva Atendido; si no existe devuelve false")
    void eliminarAtendidaOInexistente() {
        Reserva r = servicio.generarReserva(dto(1, "10:00"));
        forzarEstado(r.getId(), "Atendido");

        assertThrows(IllegalArgumentException.class, () -> servicio.eliminarReserva(r.getId()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservas_turnos", Integer.class)).isEqualTo(1);
        assertThat(servicio.eliminarReserva(999)).isFalse();
    }

    @Test
    @DisplayName("Listar reservas: todas o filtradas por profesional")
    void listarReservas() {
        servicio.generarReserva(dto(1, "09:00"));
        servicio.generarReserva(dto(1, "10:00"));
        em.flush();

        assertThat(servicio.listarTodas(Optional.empty())).hasSize(2);
        assertThat(servicio.listarTodas(Optional.of(1))).hasSize(2);
        assertThat(servicio.listarTodas(Optional.of(2))).isEmpty();
    }
}