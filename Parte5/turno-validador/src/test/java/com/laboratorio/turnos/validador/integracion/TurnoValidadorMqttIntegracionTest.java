package com.laboratorio.turnos.validador.integracion;

import com.laboratorio.turnos.validador.mqtt.ValidadorMqtt;
import com.laboratorio.turnos.validador.service.ColaValidacion;
import com.laboratorio.turnos.validador.service.TurnoValidadorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// mockea mosquitto
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({TurnoValidadorService.class, ColaValidacion.class})
@DisplayName("TurnoValidadorService: turnos atendidos y publicacion MQTT (mock)")
class TurnoValidadorMqttIntegracionTest extends IntegracionJPABaseTest {

    @MockBean ValidadorMqtt mqtt;
    @Autowired TurnoValidadorService servicio;

    @BeforeEach
    void sembrar() {
        limpiar();
        jdbc.update("INSERT INTO establecimientos (id, nombre_comercial, direccion, telefono, "
                + "correo_electronico, horario_apertura, horario_cierre) "
                + "VALUES (1, 'Clinica Test', 'Calle 1', '099111111', 'a@b.com', '08:00', '18:00')");
        jdbc.update("INSERT INTO personal (id, id_establecimiento, nombre, especialidad, costo_consulta, estado) "
                + "VALUES (1, 1, 'Dra. Ana Perez', 'Clinica', 1500.00, 'ACTIVO')");
    }

    @AfterEach
    void limpiar() {
        for (String tabla : new String[]{"reservas_turnos", "personal", "establecimientos"}) {
            jdbc.update("DELETE FROM " + tabla);
        }
    }

    private void sembrarAgendado(String fecha) {
        jdbc.update("INSERT INTO reservas_turnos (id, id_personal, email_solicitante, telefono_solicitante, "
                + "fecha_turno, hora_turno, fecha_registro, estado) "
                + "VALUES (1, 1, 'c@d.com', '099222222', ?, '10:00', NOW(), 'Agendado')", fecha);
    }

    private String estado() {
        return jdbc.queryForObject("SELECT estado FROM reservas_turnos WHERE id = 1", String.class);
    }

    @Test
    @DisplayName("Un turno Agendado cuya fecha ya paso queda Atendido y se pide publicar el evento")
    void agendadoPasadoPasaAAtendidoYPublica() throws Exception {
        sembrarAgendado("2020-01-01");

        servicio.revisarTurnosNuevos();

        assertThat(estado()).isEqualTo("Atendido");
        verify(mqtt).publicarTurnoAtendido(1);
    }

    @Test
    @DisplayName("Un turno Agendado de fecha futura no se marca Atendido ni se publica nada")
    void agendadoFuturoNoSePublica() throws Exception {
        sembrarAgendado("2030-01-01");

        servicio.revisarTurnosNuevos();

        assertThat(estado()).isEqualTo("Agendado");
        verify(mqtt, never()).publicarTurnoAtendido(any());
    }

    @Test
    @DisplayName("Si falla la publicacion MQTT, el estado Atendido igual queda guardado")
    void fallaMqttNoRevierteElEstado() throws Exception {
        sembrarAgendado("2020-01-01");
        doThrow(new RuntimeException("broker caido")).when(mqtt).publicarTurnoAtendido(1);

        assertDoesNotThrow(() -> servicio.revisarTurnosNuevos());

        assertThat(estado()).isEqualTo("Atendido");
    }
}