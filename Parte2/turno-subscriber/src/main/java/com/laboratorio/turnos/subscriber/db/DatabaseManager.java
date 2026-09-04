package com.laboratorio.turnos.subscriber.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Administrador de conexiones y ciclo de vida del esquema de base de datos MariaDB.
 */
public class DatabaseManager implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    private final String url;
    private final String user;
    private final String password;

    public DatabaseManager(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /**
     * Obtiene una nueva conexión a MariaDB.
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Espera e inicializa la base de datos, creando el esquema DDL y datos semilla
     * si aún no han sido cargados.
     */
    public void waitAndInitialize() {
        boolean ready = false;
        int retries = 0;
        final int maxRetries = 20;

        while (!ready && retries < maxRetries) {
            try (Connection conn = getConnection()) {
                logger.info("Conexión con MariaDB establecida exitosamente.");
                initSchema(conn);
                seedInitialData(conn);
                ready = true;
            } catch (SQLException e) {
                retries++;
                logger.warn("Esperando disponibilidad de MariaDB (intento {}/{}): {}", retries, maxRetries, e.getMessage());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        if (!ready) {
            throw new RuntimeException("No fue posible conectar a MariaDB luego de varios reintentos.");
        }
    }

    private void initSchema(Connection conn) throws SQLException {
        logger.info("Verificando e inicializando esquema de tablas...");
        try (Statement stmt = conn.createStatement()) {

            // Tabla 1: Establecimientos
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS establecimientos (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    nombre_comercial VARCHAR(150) NOT NULL,
                    direccion VARCHAR(255) NOT NULL,
                    telefono VARCHAR(50) NOT NULL,
                    correo_electronico VARCHAR(100) NOT NULL,
                    horario_apertura TIME NOT NULL,
                    horario_cierre TIME NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            // Tabla 2: Personal
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS personal (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    id_establecimiento INT NOT NULL,
                    nombre VARCHAR(150) NOT NULL,
                    especialidad VARCHAR(100) NOT NULL,
                    costo_consulta DECIMAL(10,2) NOT NULL,
                    duracion_estandar_minutos INT NOT NULL DEFAULT 30,
                    estado ENUM('ACTIVO', 'INACTIVO') NOT NULL DEFAULT 'ACTIVO',
                    INDEX idx_personal_est (id_establecimiento),
                    CONSTRAINT fk_personal_establecimiento
                        FOREIGN KEY (id_establecimiento) REFERENCES establecimientos(id)
                        ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            // Tabla 3: Reservas de Turnos
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS reservas_turnos (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    id_personal INT NOT NULL,
                    email_solicitante VARCHAR(100) NOT NULL,
                    telefono_solicitante VARCHAR(50) NOT NULL,
                    fecha_turno DATE NOT NULL,
                    hora_turno TIME NOT NULL,
                    duracion_minutos INT NOT NULL DEFAULT 30,
                    fecha_registro DATETIME NOT NULL,
                    estado VARCHAR(30) NOT NULL DEFAULT 'CONFIRMADO',
                    INDEX idx_reserva_personal (id_personal),
                    CONSTRAINT fk_reserva_personal
                        FOREIGN KEY (id_personal) REFERENCES personal(id)
                        ON DELETE RESTRICT,
                    CONSTRAINT uq_personal_fecha_hora
                        UNIQUE KEY (id_personal, fecha_turno, hora_turno)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            logger.info("Esquema de base de datos verificado y listo.");
        }
    }

    private void seedInitialData(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM establecimientos");
            if (rs.next() && rs.getInt(1) == 0) {
                logger.info("Cargando datos semilla de establecimientos y personal...");

                // Establecimientos iniciales
                stmt.executeUpdate("""
                    INSERT INTO establecimientos (id, nombre_comercial, direccion, telefono, correo_electronico, horario_apertura, horario_cierre)
                    VALUES
                    (1, 'Centro de Estética & Barbería Central', 'Av. 18 de Julio 1420', '099112233', 'contacto@barberiacentral.uy', '08:00:00', '19:00:00'),
                    (2, 'Taller Mecánico & Servicios Rápidos', 'Bvar. Artigas 3250', '098445566', 'info@tallerapido.uy', '09:00:00', '18:00:00');
                """);

                // Personal inicial (incluyendo activos, un inactivo para pruebas de reglas de negocio, y el ID 8 de Parte 1)
                stmt.executeUpdate("""
                    INSERT INTO personal (id, id_establecimiento, nombre, especialidad, costo_consulta, duracion_estandar_minutos, estado)
                    VALUES
                    (1, 1, 'Dra. Sofía Martínez', 'Estética Facial', 1500.00, 30, 'ACTIVO'),
                    (2, 1, 'Carlos Gómez', 'Barbero / Estilista', 800.00, 30, 'ACTIVO'),
                    (3, 2, 'Martín Rodríguez', 'Mecánica General', 2200.00, 30, 'ACTIVO'),
                    (4, 1, 'Lucía Fernández', 'Cosmetología', 1200.00, 30, 'INACTIVO'),
                    (8, 1, 'Juan Pérez', 'Peluquería', 900.00, 30, 'ACTIVO');
                """);

                logger.info("Datos iniciales de establecimientos y personal insertados exitosamente.");
            } else {
                logger.info("Los datos semilla ya se encuentran presentes en la base de datos.");
            }
        }
    }

    @Override
    public void close() {
        // En caso de usar pool de conexiones se liberaría aquí
    }
}
