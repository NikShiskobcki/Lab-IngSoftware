package com.laboratorio.turnos.api.integracion;

import com.laboratorio.turnos.api.exception.ResourceNotFoundException;
import com.laboratorio.turnos.api.personal.PersonalConsultaService;
import com.laboratorio.turnos.api.personal.dto.AgendaResponse;
import com.laboratorio.turnos.api.personal.dto.DisponibilidadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Import(PersonalConsultaService.class)
@DisplayName("Nivel 2 | PersonalConsultaService con repositorios reales y MariaDB")
class PersonalConsultaServiceIntegracionTest extends IntegracionJpaBaseTest {
/*
    private static final LocalDate FECHA = LocalDate.of(2030, 1, 1);

    @Autowired PersonalConsultaService servicio;

    @BeforeEach
    void datosBase() {
        jdbc.update("INSERT INTO establecimientos (id, nombre_comercial, direccion, telefono, "
                + "correo_electronico, horario_apertura, horario_cierre) "
                + "VALUES (1, 'Clinica Test', 'Calle 1', '099111111', 'a@b.com', '08:00', '18:00')");
        jdbc.update("INSERT INTO personal (id, id_establecimiento, nombre, especialidad, costo_consulta, estado) "
                + "VALUES (1, 1, 'Dra. Ana Perez', 'Clinica', 1500.00, 'ACTIVO')");
        jdbc.update("INSERT INTO personal (id, id_establecimiento, nombre, especialidad, costo_consulta, estado) "
                + "VALUES (2, 1, 'Dr. Inactivo', 'Clinica', 1500.00, 'INACTIVO')");
    }

    private void sembrarReserva(int idPersonal, String hora, String estado) {
        jdbc.update("INSERT INTO reservas_turnos (id_personal, id_establecimiento, email_solicitante, "
                        + "telefono_solicitante, fecha_turno, hora_turno, fecha_registro, estado) "
                        + "VALUES (?, 1, 'c@d.com', '099222222', '2030-01-01', ?, NOW(), ?)",
                idPersonal, hora, estado);
    }

    @Test
    @DisplayName("Profesional activo sin reservas: ofrece los 20 horarios de 30 min entre apertura y cierre")
    void sinReservasOfreceTodosLosHorarios() {
        DisponibilidadResponse r = servicio.consultarDisponibilidad(1L, FECHA);

        List<LocalTime> horarios = r.getHorariosDisponibles();
        assertThat(horarios).hasSize(20);
        assertThat(horarios.get(0)).isEqualTo(LocalTime.of(8, 0));
        assertThat(horarios.get(19)).isEqualTo(LocalTime.of(17, 30));
    }

    // el validador deja estos estados cuando un turno queda tomado: el horario no puede verse como libre
    @ParameterizedTest(name = "Una reserva en estado {0} oculta ese horario")
    @ValueSource(strings = {"Agendado", "Atendido", "Facturado"})
    void reservaTomadaOcultaElHorario(String estado) {
        sembrarReserva(1, "10:00", estado);

        List<LocalTime> horarios = servicio.consultarDisponibilidad(1L, FECHA).getHorariosDisponibles();

        assertThat(horarios).doesNotContain(LocalTime.of(10, 0));
        assertThat(horarios).hasSize(19);
    }

    @Test
    @DisplayName("Una reserva rechazada no oculta el horario")
    void reservaRechazadaNoOculta() {
        sembrarReserva(1, "10:00", "Rechazado/Turno Ocupado");

        List<LocalTime> horarios = servicio.consultarDisponibilidad(1L, FECHA).getHorariosDisponibles();

        assertThat(horarios).contains(LocalTime.of(10, 0));
    }

    @Test
    @DisplayName("Profesional inactivo: no hay horarios disponibles")
    void profesionalInactivoSinHorarios() {
        DisponibilidadResponse r = servicio.consultarDisponibilidad(2L, FECHA);

        assertThat(r.getHorariosDisponibles()).isEmpty();
    }

    @Test
    @DisplayName("Profesional inexistente: ResourceNotFoundException en disponibilidad y en agenda")
    void profesionalInexistente() {
        assertThrows(ResourceNotFoundException.class, () -> servicio.consultarDisponibilidad(999L, FECHA));
        assertThrows(ResourceNotFoundException.class, () -> servicio.consultarAgenda(999L, FECHA));
    }

    @Test
    @DisplayName("La agenda trae solo los turnos del profesional y ese dia, ordenados por hora")
    void agendaOrdenadaPorHora() {
        sembrarReserva(1, "11:00", "Agendado");
        sembrarReserva(1, "09:00", "SOLICITADO");
        sembrarReserva(1, "10:00", "Agendado");
        sembrarReserva(2, "08:00", "Agendado");   // otro profesional

        AgendaResponse agenda = servicio.consultarAgenda(1L, FECHA);

        assertThat(agenda.getNombrePersonal()).isEqualTo("Dra. Ana Perez");
        assertThat(agenda.getTurnos()).extracting(t -> t.getHora())
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0));
        assertThat(agenda.getTurnos().get(0).getEstado()).isEqualTo("SOLICITADO");
    }*/
}