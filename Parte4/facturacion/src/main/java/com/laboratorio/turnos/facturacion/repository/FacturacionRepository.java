package com.laboratorio.turnos.facturacion.repository;

import com.laboratorio.turnos.facturacion.model.TurnoAtendido;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// consultas sql del proceso de facturacion; todas operan sobre la conexion (y transaccion) recibida
public class FacturacionRepository {

    public static final String ESTADO_ATENDIDO = "Atendido";
    public static final String ESTADO_FACTURADO = "Facturado";

    // ids de todos los turnos pendientes de facturar, del mas antiguo al mas nuevo
    public List<Integer> buscarIdsTurnosAtendidos(Connection conn) throws SQLException {
        String sql = "SELECT id FROM reservas_turnos WHERE estado = ? ORDER BY fecha_turno, hora_turno, id";
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ESTADO_ATENDIDO);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    // bloquea el turno (for update) solo si sigue en estado atendido; vacio si otro proceso ya lo facturo
    public Optional<TurnoAtendido> bloquearTurnoAtendido(Connection conn, int idTurno) throws SQLException {
        String sqlTurno = """
            SELECT id, id_personal, email_solicitante, telefono_solicitante, fecha_turno, hora_turno
            FROM reservas_turnos
            WHERE id = ? AND estado = ?
            FOR UPDATE
            """;
        int idPersonal;
        String email;
        String telefono;
        java.time.LocalDate fecha;
        java.time.LocalTime hora;

        try (PreparedStatement ps = conn.prepareStatement(sqlTurno)) {
            ps.setInt(1, idTurno);
            ps.setString(2, ESTADO_ATENDIDO);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                idPersonal = rs.getInt("id_personal");
                email = rs.getString("email_solicitante");
                telefono = rs.getString("telefono_solicitante");
                fecha = rs.getDate("fecha_turno").toLocalDate();
                hora = rs.getTime("hora_turno").toLocalTime();
            }
        }

        String sqlPersonal = "SELECT nombre, especialidad, costo_consulta FROM personal WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlPersonal)) {
            ps.setInt(1, idPersonal);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("el personal id=" + idPersonal + " del turno id=" + idTurno + " no existe");
                }
                return Optional.of(new TurnoAtendido(idTurno, idPersonal, email, telefono, fecha, hora,
                        rs.getString("nombre"), rs.getString("especialidad"), rs.getBigDecimal("costo_consulta")));
            }
        }
    }

    // busca el cliente por email; si no existe lo crea
    public int obtenerOCrearCliente(Connection conn, String email, String telefono) throws SQLException {
        Optional<Integer> existente = buscarCliente(conn, email);
        if (existente.isPresent()) {
            return existente.get();
        }
        String sql = "INSERT INTO clientes (email, telefono, fecha_alta) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email);
            ps.setString(2, telefono);
            ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            if (esClaveDuplicada(e)) {
                // otro proceso lo creo en el medio
                return buscarCliente(conn, email).orElseThrow(() -> e);
            }
            throw e;
        }
    }

    private Optional<Integer> buscarCliente(Connection conn, String email) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM clientes WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getInt(1)) : Optional.empty();
            }
        }
    }

    // busca la factura del cliente para el mes (la bloquea); si no existe la crea abierta
    public int obtenerOCrearFactura(Connection conn, int idCliente, int anio, int mes) throws SQLException {
        Optional<Integer> existente = buscarFactura(conn, idCliente, anio, mes);
        if (existente.isPresent()) {
            return existente.get();
        }
        String sql = "INSERT INTO facturas (id_cliente, anio, mes, total, estado, fecha_creacion) VALUES (?, ?, ?, 0, 'ABIERTA', ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, idCliente);
            ps.setInt(2, anio);
            ps.setInt(3, mes);
            ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            if (esClaveDuplicada(e)) {
                return buscarFactura(conn, idCliente, anio, mes).orElseThrow(() -> e);
            }
            throw e;
        }
    }

    private Optional<Integer> buscarFactura(Connection conn, int idCliente, int anio, int mes) throws SQLException {
        String sql = "SELECT id FROM facturas WHERE id_cliente = ? AND anio = ? AND mes = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCliente);
            ps.setInt(2, anio);
            ps.setInt(3, mes);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getInt(1)) : Optional.empty();
            }
        }
    }

    public void insertarItem(Connection conn, int idFactura, int idTurno, String descripcion, java.math.BigDecimal monto)
            throws SQLException {
        String sql = "INSERT INTO items_factura (id_factura, id_turno, descripcion, monto) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idFactura);
            ps.setInt(2, idTurno);
            ps.setString(3, descripcion);
            ps.setBigDecimal(4, monto);
            ps.executeUpdate();
        }
    }

    // el total siempre se recalcula como la suma de los items
    public void recalcularTotal(Connection conn, int idFactura) throws SQLException {
        String sql = "UPDATE facturas SET total = (SELECT COALESCE(SUM(monto), 0) FROM items_factura WHERE id_factura = ?) WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idFactura);
            ps.setInt(2, idFactura);
            ps.executeUpdate();
        }
    }

    // pasa el turno de atendido a facturado; devuelve false si no estaba atendido
    public boolean marcarFacturado(Connection conn, int idTurno) throws SQLException {
        String sql = "UPDATE reservas_turnos SET estado = ? WHERE id = ? AND estado = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ESTADO_FACTURADO);
            ps.setInt(2, idTurno);
            ps.setString(3, ESTADO_ATENDIDO);
            return ps.executeUpdate() == 1;
        }
    }

    private boolean esClaveDuplicada(SQLException e) {
        return e.getErrorCode() == 1062 || "23000".equals(e.getSQLState());
    }
}
