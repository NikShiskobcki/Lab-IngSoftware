package com.laboratorio.turnos.validador.integracion;

import com.laboratorio.turnos.validador.model.Personal;
import com.laboratorio.turnos.validador.model.ReservaTurno;
import com.laboratorio.turnos.validador.repository.EstablecimientoRepository;
import com.laboratorio.turnos.validador.repository.PersonalRepository;
import com.laboratorio.turnos.validador.repository.ReservaTurnoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Repositorios del validador contra MariaDB real")
class RepositoriosValidadorIntegracionTest extends IntegracionJPABaseTest {

    @Autowired ReservaTurnoRepository reservas;
    @Autowired PersonalRepository personal;
    @Autowired EstablecimientoRepository establecimientos;

    @BeforeEach
    void datosBase() {
        jdbc.update("INSERT INTO establecimientos (id, nombre_comercial, direccion, telefono, "
                + "correo_electronico, horario_apertura, horario_cierre) "
                + "VALUES (1, 'Clinica Test', 'Calle 1', '099111111', 'a@b.com', '08:00', '18:00')");
        sembrarPersonal(1, "ACTIVO");
        sembrarPersonal(2, "INACTIVO");
    }

    private void sembrarPersonal(int id, String estado) {
        jdbc.update("INSERT INTO personal (id, id_establecimiento, nombre, especialidad, "
                + "costo_consulta, estado) VALUES (?, 1, ?, 'Clinica', 1500.00, ?)",
                id, "Profesional " + id, estado);
    }

    private void sembrarReserva(int idPersonal, String fecha, String hora, String estado) {
        jdbc.update("INSERT INTO reservas_turnos (id_personal, id_establecimiento, email_solicitante, "
                + "telefono_solicitante, fecha_turno, hora_turno, duracion_minutos, fecha_registro, estado) "
                + "VALUES (?, 1, 'c@d.com', '099222222', ?, ?, 30, NOW(), ?)",
                idPersonal, fecha, hora, estado);
    }

    @Test
    @DisplayName("findByIdPersonalAndFechaTurno devuelve solo los turnos de ese profesional y ese dia")
    void turnosDelProfesionalEnUnDia() {
        sembrarReserva(1, "2030-01-01", "10:00", "Agendado");
        sembrarReserva(1, "2030-01-01", "11:00", "SOLICITADO");
        sembrarReserva(1, "2030-01-02", "10:00", "Agendado");   // otro dia
        sembrarReserva(2, "2030-01-01", "10:00", "Agendado");   // otro profesional

        List<ReservaTurno> turnos = reservas.findByIdPersonalAndFechaTurno(1, LocalDate.of(2030, 1, 1));

        assertThat(turnos).hasSize(2);
        assertThat(turnos).extracting(ReservaTurno::getHoraTurno)
                .containsExactlyInAnyOrder(LocalTime.of(10, 0), LocalTime.of(11, 0));
    }

    @Test
    @DisplayName("findByEstadoIn filtra por la lista de estados pedida")
    void filtroPorEstados() {
        sembrarReserva(1, "2030-01-01", "10:00", "Agendado");
        sembrarReserva(1, "2030-01-01", "11:00", "Atendido");
        sembrarReserva(1, "2030-01-01", "12:00", "Rechazado/Turno Ocupado");

        assertThat(reservas.findByEstadoIn(List.of("Agendado"))).hasSize(1);
        assertThat(reservas.findByEstadoIn(List.of("Agendado", "Atendido"))).hasSize(2);
    }

    @Test
    @DisplayName("Guardar una ReservaTurno persiste cada campo en su columna")
    void guardarMapeaLasColumnas() {
        ReservaTurno r = new ReservaTurno();
        r.setIdPersonal(1);
        r.setIdEstablecimiento(1);
        r.setEmailSolicitante("cliente@test.com");
        r.setTelefonoSolicitante("099333333");
        r.setFechaTurno(LocalDate.of(2030, 3, 15));
        r.setHoraTurno(LocalTime.of(9, 30));
        r.setDuracionMinutos(30);
        r.setFechaRegistro(LocalDateTime.now());
        r.setEstado("SOLICITADO");

        Integer id = reservas.saveAndFlush(r).getId();

        assertThat(jdbc.queryForObject(
                "SELECT email_solicitante FROM reservas_turnos WHERE id = ?", String.class, id))
                .isEqualTo("cliente@test.com");
        assertThat(jdbc.queryForObject(
                "SELECT estado FROM reservas_turnos WHERE id = ?", String.class, id))
                .isEqualTo("SOLICITADO");
        assertThat(jdbc.queryForObject(
                "SELECT id_establecimiento FROM reservas_turnos WHERE id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("PersonalRepository y EstablecimientoRepository leen lo que hay en la BD")
    void lecturaDePersonalYEstablecimiento() {
        Personal activo = personal.findById(1).orElseThrow();
        assertThat(activo.getEstado()).isEqualTo("ACTIVO");
        assertThat(activo.getIdEstablecimiento()).isEqualTo(1);
        assertThat(personal.findById(2).orElseThrow().getEstado()).isEqualTo("INACTIVO");
        assertThat(personal.findById(999)).isEmpty();

        assertThat(establecimientos.existsById(1)).isTrue();
        assertThat(establecimientos.existsById(999)).isFalse();
    }
}