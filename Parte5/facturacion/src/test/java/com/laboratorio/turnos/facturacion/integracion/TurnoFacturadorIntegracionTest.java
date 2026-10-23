package com.laboratorio.turnos.facturacion.integracion;

import com.laboratorio.turnos.facturacion.model.ResumenCiclo;
import com.laboratorio.turnos.facturacion.service.ColaFacturacion;
import com.laboratorio.turnos.facturacion.service.FacturacionService;
import com.laboratorio.turnos.facturacion.service.TurnoFacturador;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import({TurnoFacturador.class, FacturacionService.class, ColaFacturacion.class})
@DisplayName("Nivel 2 | Facturacion con repositorios reales y MariaDB")
class TurnoFacturadorIntegracionTest extends IntegracionJpaBaseTest {

    @Autowired TurnoFacturador facturador;
    @Autowired FacturacionService servicio;
    @Autowired ColaFacturacion cola;
    @Autowired EntityManager em;

    @BeforeEach
    void datosBase() {
        jdbc.update("INSERT INTO establecimientos (id, nombre_comercial, direccion, telefono, "
                + "correo_electronico, horario_apertura, horario_cierre) "
                + "VALUES (1, 'Clinica Test', 'Calle 1', '099111111', 'a@b.com', '08:00', '18:00')");
        jdbc.update("INSERT INTO personal (id, id_establecimiento, nombre, especialidad, costo_consulta) "
                + "VALUES (1, 1, 'Dra. Ana Perez', 'Clinica', 1500.00)");
    }

    private void sembrarReserva(int id, String email, String fecha, String hora, String estado) {
        jdbc.update("INSERT INTO reservas_turnos (id, id_personal, email_solicitante, telefono_solicitante, "
                        + "fecha_turno, hora_turno, fecha_registro, estado) VALUES (?, 1, ?, '099222222', ?, ?, NOW(), ?)",
                id, email, fecha, hora, estado);
    }

    // los cambios de hibernate se envian a la base con flush
    private int contar(String tabla) {
        em.flush();
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
    }

    private String estadoReserva(int id) {
        em.flush();
        return jdbc.queryForObject("SELECT estado FROM reservas_turnos WHERE id = ?", String.class, id);
    }

    private BigDecimal totalFactura() {
        em.flush();
        return jdbc.queryForObject("SELECT total FROM facturas", BigDecimal.class);
    }

    @Test
    @DisplayName("Factura un turno atendido: item, total, cliente y estado Facturado")
    void facturaUnTurnoAtendido() {
        sembrarReserva(1, "cliente@test.com", "2030-01-15", "10:00", "Atendido");

        assertThat(facturador.facturar(1)).isTrue();

        assertThat(estadoReserva(1)).isEqualTo("Facturado");
        assertThat(contar("clientes")).isEqualTo(1);
        assertThat(contar("facturas")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT anio FROM facturas", Integer.class)).isEqualTo(2030);
        assertThat(jdbc.queryForObject("SELECT mes FROM facturas", Integer.class)).isEqualTo(1);
        assertThat(totalFactura()).isEqualByComparingTo("1500.00");
        assertThat(jdbc.queryForObject("SELECT descripcion FROM items_factura", String.class))
                .isEqualTo("Turno #1 - Dra. Ana Perez (Clinica) - 2030-01-15 10:00");
    }

    @Test
    @DisplayName("Dos turnos del mismo cliente y mes van a la misma factura y el total suma ambos")
    void mismoClienteMismoMes() {
        sembrarReserva(1, "cliente@test.com", "2030-01-15", "10:00", "Atendido");
        sembrarReserva(2, "cliente@test.com", "2030-01-20", "11:00", "Atendido");

        facturador.facturar(1);
        facturador.facturar(2);

        assertThat(contar("clientes")).isEqualTo(1);
        assertThat(contar("facturas")).isEqualTo(1);
        assertThat(contar("items_factura")).isEqualTo(2);
        assertThat(totalFactura()).isEqualByComparingTo("3000.00");
    }

    @Test
    @DisplayName("Turnos de meses distintos generan facturas distintas (mes del turno)")
    void mesesDistintos() {
        sembrarReserva(1, "cliente@test.com", "2030-01-15", "10:00", "Atendido");
        sembrarReserva(2, "cliente@test.com", "2030-02-03", "10:00", "Atendido");

        facturador.facturar(1);
        facturador.facturar(2);

        assertThat(contar("facturas")).isEqualTo(2);
        List<Integer> meses = jdbc.queryForList("SELECT mes FROM facturas ORDER BY mes", Integer.class);
        assertThat(meses).containsExactly(1, 2);
    }

    @Test
    @DisplayName("El email del cliente se normaliza: mayusculas y espacios no crean otro cliente")
    void emailNormalizado() {
        sembrarReserva(1, "  Cliente@Test.com ", "2030-01-15", "10:00", "Atendido");
        sembrarReserva(2, "cliente@test.com", "2030-01-20", "11:00", "Atendido");

        facturador.facturar(1);
        facturador.facturar(2);

        assertThat(contar("clientes")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT email FROM clientes", String.class))
                .isEqualTo("cliente@test.com");
    }

    @Test
    @DisplayName("Un turno que no esta Atendido no se factura")
    void turnoNoAtendidoSeOmite() {
        sembrarReserva(1, "cliente@test.com", "2030-01-15", "10:00", "Agendado");

        assertThat(facturador.facturar(1)).isFalse();

        assertThat(estadoReserva(1)).isEqualTo("Agendado");
        assertThat(contar("clientes")).isEqualTo(0);
        assertThat(contar("items_factura")).isEqualTo(0);
    }

    @Test
    @DisplayName("Facturar dos veces el mismo turno: la segunda se omite y no duplica el item")
    void noSeFacturaDosVeces() {
        sembrarReserva(1, "cliente@test.com", "2030-01-15", "10:00", "Atendido");

        assertThat(facturador.facturar(1)).isTrue();
        assertThat(facturador.facturar(1)).isFalse();

        assertThat(contar("items_factura")).isEqualTo(1);
        assertThat(totalFactura()).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("El ciclo cuenta facturados y omitidos (turno en otro estado o inexistente)")
    void cicloConTurnosMezclados() {
        sembrarReserva(1, "cliente@test.com", "2030-01-15", "10:00", "Atendido");
        sembrarReserva(2, "cliente@test.com", "2030-01-15", "11:00", "Agendado");
        cola.agregar(1);
        cola.agregar(2);
        cola.agregar(999);

        ResumenCiclo resumen = servicio.ejecutarCiclo();

        assertThat(resumen).isEqualTo(new ResumenCiclo(3, 1, 2, 0));
        assertThat(estadoReserva(1)).isEqualTo("Facturado");
        assertThat(estadoReserva(2)).isEqualTo("Agendado");
    }
}