package com.laboratorio.turnos.validador.integracion;

import com.laboratorio.turnos.validador.mqtt.ValidadorMqtt;
import com.laboratorio.turnos.validador.service.ColaValidacion;
import com.laboratorio.turnos.validador.service.TurnoValidadorService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Import({TurnoValidadorService.class, ColaValidacion.class})
@DisplayName("Nivel 2 | TurnoValidadorService con repositorios reales y MariaDB (MQTT simulado)")
class TurnoValidadorServiceIntegracionTest extends IntegracionJPABaseTest {

    @MockBean ValidadorMqtt mqtt;   // el broker queda fuera de estas pruebas
    @Autowired TurnoValidadorService servicio;
    @Autowired ColaValidacion cola;
    @Autowired EntityManager em;

    private static final String RECHAZO_INVALIDA = "Rechazado/Solicitud No Valida";
    private static final String RECHAZO_OCUPADO = "Rechazado/Turno Ocupado";

    @BeforeEach
    void datosBase() {
        jdbc.update("INSERT INTO establecimientos (id, nombre_comercial, direccion, telefono, "
                + "correo_electronico, horario_apertura, horario_cierre) "
                + "VALUES (1, 'Clinica Test', 'Calle 1', '099111111', 'a@b.com', '08:00', '18:00')");
        jdbc.update("INSERT INTO establecimientos (id, nombre_comercial, direccion, telefono, "
                + "correo_electronico, horario_apertura, horario_cierre) "
                + "VALUES (2, 'Otra Clinica', 'Calle 2', '099444444', 'o@b.com', '08:00', '18:00')");
        jdbc.update("INSERT INTO personal (id, id_establecimiento, nombre, especialidad, costo_consulta, estado) "
                + "VALUES (1, 1, 'Dra. Ana Perez', 'Clinica', 1500.00, 'ACTIVO')");
        jdbc.update("INSERT INTO personal (id, id_establecimiento, nombre, especialidad, costo_consulta, estado) "
                + "VALUES (2, 1, 'Dr. Inactivo', 'Clinica', 1500.00, 'INACTIVO')");
    }

    private void sembrarReserva(int id, int idPersonal, Integer idEstablecimiento,
                                String fecha, String hora, String estado) {
        jdbc.update("INSERT INTO reservas_turnos (id, id_personal, id_establecimiento, email_solicitante, "
                        + "telefono_solicitante, fecha_turno, hora_turno, fecha_registro, estado) "
                        + "VALUES (?, ?, ?, 'c@d.com', '099222222', ?, ?, NOW(), ?)",
                id, idPersonal, idEstablecimiento, fecha, hora, estado);
    }

    // encola los ids (como si llegaran por MQTT) y ejecuta un ciclo de revision
    private void procesar(int... ids) {
        for (int id : ids) {
            cola.agregar(id);
        }
        servicio.revisarTurnosNuevos();
        em.flush();
    }

    private String estado(int id) {
        em.flush();
        return jdbc.queryForObject("SELECT estado FROM reservas_turnos WHERE id = ?", String.class, id);
    }

    @Test
    @DisplayName("Una solicitud valida en horario libre queda Agendado")
    void turnoLibreQuedaAgendado() {
        sembrarReserva(1, 1, null, "2030-01-01", "10:00", "SOLICITADO");
        procesar(1);
        assertThat(estado(1)).isEqualTo("Agendado");
    }

    @Test
    @DisplayName("Si el horario esta ocupado por un turno Agendado, la nueva queda Rechazado/Turno Ocupado")
    void horarioOcupadoPorAgendado() {
        sembrarReserva(1, 1, null, "2030-01-01", "10:00", "Agendado");
        sembrarReserva(2, 1, null, "2030-01-01", "10:00", "SOLICITADO");
        procesar(2);
        assertThat(estado(2)).isEqualTo(RECHAZO_OCUPADO);
        assertThat(estado(1)).isEqualTo("Agendado");
    }

    @Test
    @DisplayName("Dos solicitudes para el mismo horario en el mismo ciclo: una se agenda y la otra se rechaza")
    void dobleReservaEnElMismoCiclo() {
        sembrarReserva(1, 1, null, "2030-01-01", "10:00", "SOLICITADO");
        sembrarReserva(2, 1, null, "2030-01-01", "10:00", "SOLICITADO");
        procesar(1, 2);
        assertThat(estado(1)).isEqualTo("Agendado");
        assertThat(estado(2)).isEqualTo(RECHAZO_OCUPADO);
    }

    @Test
    @DisplayName("Turnos contiguos (10:00 y 10:30) no chocan y se agendan los dos")
    void turnosContiguos() {
        sembrarReserva(1, 1, null, "2030-01-01", "10:00", "SOLICITADO");
        sembrarReserva(2, 1, null, "2030-01-01", "10:30", "SOLICITADO");
        procesar(1, 2);
        assertThat(estado(1)).isEqualTo("Agendado");
        assertThat(estado(2)).isEqualTo("Agendado");
    }

    @Test
    @DisplayName("Un turno rechazado previamente no bloquea el horario")
    void rechazadoNoBloquea() {
        sembrarReserva(1, 1, null, "2030-01-01", "10:00", RECHAZO_OCUPADO);
        sembrarReserva(2, 1, null, "2030-01-01", "10:00", "SOLICITADO");
        procesar(2);
        assertThat(estado(2)).isEqualTo("Agendado");
    }

    @Test
    @DisplayName("Profesional inactivo: Rechazado/Solicitud No Valida")
    void profesionalInactivo() {
        sembrarReserva(1, 2, null, "2030-01-01", "10:00", "SOLICITADO");
        procesar(1);
        assertThat(estado(1)).isEqualTo(RECHAZO_INVALIDA);
    }

    @Test
    @DisplayName("El profesional no pertenece al establecimiento de la reserva: Rechazado/Solicitud No Valida")
    void establecimientoDistintoAlDelProfesional() {
        sembrarReserva(1, 1, 2, "2030-01-01", "10:00", "SOLICITADO");
        procesar(1);
        assertThat(estado(1)).isEqualTo(RECHAZO_INVALIDA);
    }

    @Test
    @DisplayName("Establecimiento inexistente en la reserva: Rechazado/Solicitud No Valida")
    void establecimientoInexistente() {
        sembrarReserva(1, 1, 999, "2030-01-01", "10:00", "SOLICITADO");
        procesar(1);
        assertThat(estado(1)).isEqualTo(RECHAZO_INVALIDA);
    }

    @Test
    @DisplayName("Fecha y hora en el pasado: Rechazado/Solicitud No Valida")
    void fechaEnElPasado() {
        sembrarReserva(1, 1, null, "2020-01-01", "10:00", "SOLICITADO");
        procesar(1);
        assertThat(estado(1)).isEqualTo(RECHAZO_INVALIDA);
    }

    @Test
    @DisplayName("Eventos repetidos o de reservas inexistentes no rompen el ciclo ni validan dos veces")
    void eventosRepetidosOInexistentes() {
        sembrarReserva(1, 1, null, "2030-01-01", "10:00", "SOLICITADO");
        assertDoesNotThrow(() -> procesar(1, 1, 999));
        assertThat(estado(1)).isEqualTo("Agendado");
    }
}