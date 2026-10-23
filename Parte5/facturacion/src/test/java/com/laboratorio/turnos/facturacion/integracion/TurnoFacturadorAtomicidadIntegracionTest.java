package com.laboratorio.turnos.facturacion.integracion;

import com.laboratorio.turnos.facturacion.service.TurnoFacturador;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;


@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(TurnoFacturador.class)
@DisplayName("Nivel 2 | Atomicidad de TurnoFacturador (rollback real)")
class TurnoFacturadorAtomicidadIntegracionTest extends IntegracionJpaBaseTest {

    @Autowired TurnoFacturador facturador;

    @BeforeEach
    void sembrar() {
        limpiar();
        jdbc.update("INSERT INTO establecimientos (id, nombre_comercial, direccion, telefono, "
                + "correo_electronico, horario_apertura, horario_cierre) "
                + "VALUES (1, 'Clinica Test', 'Calle 1', '099111111', 'a@b.com', '08:00', '18:00')");
        jdbc.update("INSERT INTO personal (id, id_establecimiento, nombre, especialidad, costo_consulta) "
                + "VALUES (1, 1, 'Dra. Ana Perez', 'Clinica', 1500.00)");
        jdbc.update("INSERT INTO reservas_turnos (id, id_personal, email_solicitante, telefono_solicitante, "
                + "fecha_turno, hora_turno, fecha_registro, estado) "
                + "VALUES (1, 1, 'x@test.com', '099222222', '2030-01-15', '10:00', NOW(), 'Atendido')");
        // el turno 1 ya tiene un item en la factura de otro cliente (estado inconsistente)
        jdbc.update("INSERT INTO clientes (id, email, telefono, fecha_alta) "
                + "VALUES (1, 'otro@test.com', '099333333', NOW())");
        jdbc.update("INSERT INTO facturas (id, id_cliente, anio, mes, total, estado, fecha_creacion) "
                + "VALUES (1, 1, 2030, 1, 1500.00, 'ABIERTA', NOW())");
        jdbc.update("INSERT INTO items_factura (id, id_factura, id_turno, descripcion, monto) "
                + "VALUES (1, 1, 1, 'Turno ya facturado', 1500.00)");
    }

    @AfterEach
    void limpiar() {
        for (String tabla : new String[]{"items_factura", "facturas", "clientes",
                "reservas_turnos", "personal", "establecimientos"}) {
            jdbc.update("DELETE FROM " + tabla);
        }
    }

    @Test
    @DisplayName("Si falla al guardar el item, se revierte todo: turno Atendido y sin cliente ni factura nuevos")
    void fallaEnElItemRevierteTodo() {
        assertThrows(DataAccessException.class, () -> facturador.facturar(1));

        assertThat(jdbc.queryForObject("SELECT estado FROM reservas_turnos WHERE id = 1", String.class))
                .isEqualTo("Atendido");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM clientes", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM facturas", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM items_factura", Integer.class)).isEqualTo(1);
    }
}