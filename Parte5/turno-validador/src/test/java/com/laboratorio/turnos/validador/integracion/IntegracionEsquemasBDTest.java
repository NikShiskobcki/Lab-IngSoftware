package com.laboratorio.turnos.validador.integracion;

import com.laboratorio.turnos.validador.config.DatabaseInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntegracionEsquemasBDTest extends IntegracionConectarBDTest {

    private void inicializar() {
        new DatabaseInitializer(jdbc).run();
    }

    private void crearEstablecimientoYPersonal() {
        jdbc.update("INSERT INTO establecimientos (id, nombre_comercial, direccion, telefono, "
                + "correo_electronico, horario_apertura, horario_cierre) "
                + "VALUES (1, 'Clinica Test', 'Calle 1', '099111111', 'a@b.com', '08:00', '18:00')");
        jdbc.update("INSERT INTO personal (id, id_establecimiento, nombre, especialidad, costo_consulta) "
                + "VALUES (1, 1, 'Dra. Ana Perez', 'Clinica', 1500.00)");
    }

    @Test
    @DisplayName("Crea las tres tablas esperadas")
    void creaLasTresTablas() {
        inicializar();
        List<String> tablas = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class);
        assertThat(tablas).containsExactlyInAnyOrder("establecimientos", "personal", "reservas_turnos");
    }

    @Test
    @DisplayName("Ejecutarlo dos veces no falla (idempotencia)")
    void esIdempotente() {
        inicializar();
        assertDoesNotThrow(this::inicializar);
    }

    @Test
    @DisplayName("No permite personal de un establecimiento inexistente")
    void fkPersonalEstablecimiento() {
        inicializar();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO personal (id_establecimiento, nombre, especialidad, costo_consulta) "
                        + "VALUES (999, 'X', 'Y', 100)"));
    }

    @Test
    @DisplayName("No permite borrar personal que tiene reservas")
    void restrictAlBorrarPersonalConReservas() {
        inicializar();
        crearEstablecimientoYPersonal();
        jdbc.update("INSERT INTO reservas_turnos (id_personal, email_solicitante, telefono_solicitante, "
                + "fecha_turno, hora_turno, fecha_registro) "
                + "VALUES (1, 'c@d.com', '099222222', '2030-01-01', '10:00', NOW())");
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("DELETE FROM personal WHERE id = 1"));
    }

    @Test
    @DisplayName("Una reserva sin estado explicito queda SOLICITADO")
    void estadoPorDefectoSolicitado() {
        inicializar();
        crearEstablecimientoYPersonal();
        jdbc.update("INSERT INTO reservas_turnos (id_personal, email_solicitante, telefono_solicitante, "
                + "fecha_turno, hora_turno, fecha_registro) "
                + "VALUES (1, 'c@d.com', '099222222', '2030-01-01', '10:00', NOW())");
        String estado = jdbc.queryForObject("SELECT estado FROM reservas_turnos", String.class);
        assertThat(estado).isEqualTo("SOLICITADO");
    }
}