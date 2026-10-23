package com.laboratorio.turnos.facturacion.integracion;

import com.laboratorio.turnos.facturacion.model.Cliente;
import com.laboratorio.turnos.facturacion.model.Factura;
import com.laboratorio.turnos.facturacion.model.ItemFactura;
import com.laboratorio.turnos.facturacion.model.Personal;
import com.laboratorio.turnos.facturacion.model.Reserva;
import com.laboratorio.turnos.facturacion.repository.ClienteRepository;
import com.laboratorio.turnos.facturacion.repository.FacturaRepository;
import com.laboratorio.turnos.facturacion.repository.ItemFacturaRepository;
import com.laboratorio.turnos.facturacion.repository.PersonalRepository;
import com.laboratorio.turnos.facturacion.repository.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Repositorios de facturacion contra MariaDB")
class RepositoriosFacturacionIntegracionTest extends IntegracionJpaBaseTest {

    @Autowired ReservaRepository reservas;
    @Autowired PersonalRepository personal;
    @Autowired ClienteRepository clientes;
    @Autowired FacturaRepository facturas;
    @Autowired ItemFacturaRepository items;

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

    private Cliente nuevoCliente(String email) {
        return clientes.save(new Cliente(email, "099333333", LocalDateTime.now()));
    }

    @Test
    @DisplayName("buscarIdsPorEstado devuelve solo el estado pedido, ordenado por fecha, hora e id")
    void idsPorEstadoOrdenados() {
        sembrarReserva(3, "a@test.com", "2030-01-02", "09:00", "Atendido");
        sembrarReserva(1, "a@test.com", "2030-01-01", "10:00", "Atendido");
        sembrarReserva(2, "a@test.com", "2030-01-01", "09:00", "Atendido");
        sembrarReserva(4, "a@test.com", "2030-01-01", "08:00", "Agendado");

        assertThat(reservas.buscarIdsPorEstado("Atendido")).containsExactly(2, 1, 3);
    }

    @Test
    @DisplayName("bloquearPorIdYEstado devuelve el turno solo si sigue en ese estado")
    void bloqueoPorEstado() {
        sembrarReserva(1, "a@test.com", "2030-01-01", "10:00", "Atendido");

        assertThat(reservas.bloquearPorIdYEstado(1, "Atendido")).isPresent();
        assertThat(reservas.bloquearPorIdYEstado(1, "Facturado")).isEmpty();
        assertThat(reservas.bloquearPorIdYEstado(999, "Atendido")).isEmpty();
    }

    @Test
    @DisplayName("Cambiar el estado de la reserva actualiza solo esa columna")
    void cambioDeEstadoPersiste() {
        sembrarReserva(1, "a@test.com", "2030-01-01", "10:00", "Atendido");

        Reserva r = reservas.findById(1).orElseThrow();
        r.setEstado("Facturado");
        reservas.flush();

        assertThat(jdbc.queryForObject("SELECT estado FROM reservas_turnos WHERE id = 1", String.class))
                .isEqualTo("Facturado");
        assertThat(jdbc.queryForObject("SELECT email_solicitante FROM reservas_turnos WHERE id = 1", String.class))
                .isEqualTo("a@test.com");
    }

    @Test
    @DisplayName("PersonalRepository lee nombre, especialidad y costo de consulta")
    void lecturaDePersonal() {
        Personal p = personal.findById(1).orElseThrow();
        assertThat(p.getNombre()).isEqualTo("Dra. Ana Perez");
        assertThat(p.getEspecialidad()).isEqualTo("Clinica");
        assertThat(p.getCostoConsulta()).isEqualByComparingTo("1500.00");
        assertThat(personal.findById(999)).isEmpty();
    }

    @Test
    @DisplayName("Cliente: se busca por email y el email no se puede repetir")
    void clientePorEmailUnico() {
        nuevoCliente("cliente@test.com");

        assertThat(clientes.findByEmail("cliente@test.com")).isPresent();
        assertThat(clientes.findByEmail("otro@test.com")).isEmpty();
        assertThrows(DataIntegrityViolationException.class, () -> nuevoCliente("cliente@test.com"));
    }

    @Test
    @DisplayName("Factura: valores por defecto, busqueda por periodo y una sola por cliente y mes")
    void facturaPorPeriodo() {
        Cliente c = nuevoCliente("cliente@test.com");
        Factura f = facturas.saveAndFlush(new Factura(c, 2030, 1, LocalDateTime.now()));

        assertThat(f.getEstado()).isEqualTo("ABIERTA");
        assertThat(jdbc.queryForObject("SELECT total FROM facturas WHERE id = ?", BigDecimal.class, f.getId()))
                .isEqualByComparingTo("0");
        assertThat(facturas.buscarYBloquear(c.getId(), 2030, 1)).isPresent();
        assertThat(facturas.buscarYBloquear(c.getId(), 2030, 2)).isEmpty();
        assertThrows(DataIntegrityViolationException.class,
                () -> facturas.saveAndFlush(new Factura(c, 2030, 1, LocalDateTime.now())));
    }

    @Test
    @DisplayName("sumarMontos suma los items de la factura y da 0 si no tiene items")
    void sumaDeItems() {
        sembrarReserva(1, "a@test.com", "2030-01-01", "10:00", "Atendido");
        sembrarReserva(2, "a@test.com", "2030-01-01", "11:00", "Atendido");
        Cliente c = nuevoCliente("a@test.com");
        Factura conItems = facturas.save(new Factura(c, 2030, 1, LocalDateTime.now()));
        Factura vacia = facturas.save(new Factura(c, 2030, 2, LocalDateTime.now()));

        items.save(new ItemFactura(conItems, reservas.findById(1).orElseThrow(), "Turno 1", new BigDecimal("1500.00")));
        items.save(new ItemFactura(conItems, reservas.findById(2).orElseThrow(), "Turno 2", new BigDecimal("800.50")));

        assertThat(items.sumarMontos(conItems.getId())).isEqualByComparingTo("2300.50");
        assertThat(items.sumarMontos(vacia.getId())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("No se puede facturar dos veces el mismo turno (uq_item_turno)")
    void turnoNoSeFacturaDosVeces() {
        sembrarReserva(1, "a@test.com", "2030-01-01", "10:00", "Atendido");
        Cliente c = nuevoCliente("a@test.com");
        Factura f = facturas.save(new Factura(c, 2030, 1, LocalDateTime.now()));
        Reserva turno = reservas.findById(1).orElseThrow();

        items.saveAndFlush(new ItemFactura(f, turno, "Turno 1", new BigDecimal("1500.00")));

        assertThrows(DataIntegrityViolationException.class,
                () -> items.saveAndFlush(new ItemFactura(f, turno, "Turno 1 otra vez", new BigDecimal("1500.00"))));
    }
}