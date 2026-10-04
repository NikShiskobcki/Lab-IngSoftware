package com.laboratorio.turnos.facturacion.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

// conexiones a mariadb y creacion del esquema de facturacion
public class DatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    // tablas que crean otros servicios y de las que depende facturacion
    private static final List<String> TABLAS_BASE = List.of("personal", "reservas_turnos");

    private final String url;
    private final String user;
    private final String password;

    public DatabaseManager(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    // espera a mariadb y a las tablas base, luego crea las tablas de facturacion
    public void waitAndInitialize(int maxReintentos, long esperaMs) {
        for (int intento = 1; intento <= maxReintentos; intento++) {
            try (Connection conn = getConnection()) {
                for (String tabla : TABLAS_BASE) {
                    if (!existeTabla(conn, tabla)) {
                        throw new SQLException("todavia no existe la tabla '" + tabla + "'");
                    }
                }
                crearEsquema(conn);
                logger.info("base de datos lista y esquema de facturacion verificado");
                return;
            } catch (SQLException e) {
                logger.warn("esperando base de datos (intento {}/{}): {}", intento, maxReintentos, e.getMessage());
                try {
                    Thread.sleep(esperaMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("inicializacion interrumpida", ie);
                }
            }
        }
        throw new IllegalStateException("no fue posible inicializar la base de datos tras " + maxReintentos + " intentos");
    }

    private boolean existeTabla(Connection conn, String tabla) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tabla);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void crearEsquema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {

            // cliente identificado de forma unica por email
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS clientes (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    email VARCHAR(100) NOT NULL,
                    telefono VARCHAR(50) NULL,
                    fecha_alta DATETIME NOT NULL,
                    CONSTRAINT uq_cliente_email UNIQUE (email)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            // una factura por cliente y mes
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS facturas (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    id_cliente INT NOT NULL,
                    anio SMALLINT NOT NULL,
                    mes TINYINT NOT NULL,
                    total DECIMAL(12,2) NOT NULL DEFAULT 0,
                    estado VARCHAR(20) NOT NULL DEFAULT 'ABIERTA',
                    fecha_creacion DATETIME NOT NULL,
                    CONSTRAINT fk_factura_cliente FOREIGN KEY (id_cliente) REFERENCES clientes(id),
                    CONSTRAINT uq_factura_cliente_periodo UNIQUE (id_cliente, anio, mes)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            // un item por turno facturado (uq_item_turno evita facturar dos veces)
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS items_factura (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    id_factura INT NOT NULL,
                    id_turno INT NOT NULL,
                    descripcion VARCHAR(255) NOT NULL,
                    monto DECIMAL(10,2) NOT NULL,
                    CONSTRAINT fk_item_factura FOREIGN KEY (id_factura) REFERENCES facturas(id) ON DELETE CASCADE,
                    CONSTRAINT fk_item_turno FOREIGN KEY (id_turno) REFERENCES reservas_turnos(id) ON DELETE RESTRICT,
                    CONSTRAINT uq_item_turno UNIQUE (id_turno)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        }
    }
}
