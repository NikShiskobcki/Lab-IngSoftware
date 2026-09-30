package com.laboratorio.turnos.validador.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// inicializador del esquema y tablas necesarias en mariadb
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            logger.info("verificando e inicializando tablas en base de datos...");

            // tabla establecimientos
            jdbcTemplate.execute("""
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

            // tabla personal
            jdbcTemplate.execute("""
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

            // tabla reservas_turnos
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS reservas_turnos (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    id_personal INT NOT NULL,
                    id_establecimiento INT NULL,
                    email_solicitante VARCHAR(100) NOT NULL,
                    telefono_solicitante VARCHAR(50) NOT NULL,
                    fecha_turno DATE NOT NULL,
                    hora_turno TIME NOT NULL,
                    duracion_minutos INT NOT NULL DEFAULT 30,
                    fecha_registro DATETIME NOT NULL,
                    estado VARCHAR(50) NOT NULL DEFAULT 'SOLICITADO',
                    INDEX idx_reserva_personal (id_personal),
                    CONSTRAINT fk_reserva_personal
                        FOREIGN KEY (id_personal) REFERENCES personal(id)
                        ON DELETE RESTRICT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);

            // asegurar columna opcional id_establecimiento si la tabla ya existia de antes
            try {
                jdbcTemplate.execute("ALTER TABLE reservas_turnos ADD COLUMN IF NOT EXISTS id_establecimiento INT NULL;");
            } catch (Exception ignored) {}

            // eliminar restriccion de unicidad estricta para permitir que el validador maneje turno ocupado
            try {
                jdbcTemplate.execute("ALTER TABLE reservas_turnos DROP INDEX uq_personal_fecha_hora;");
            } catch (Exception ignored) {}

            // cargar datos semilla si no existen
            Integer cantEstablecimientos = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM establecimientos", Integer.class
            );

            if (cantEstablecimientos != null && cantEstablecimientos == 0) {
                logger.info("cargando datos iniciales de establecimientos y personal...");
                jdbcTemplate.execute("""
                    INSERT INTO establecimientos (id, nombre_comercial, direccion, telefono, correo_electronico, horario_apertura, horario_cierre)
                    VALUES
                    (1, 'Centro de Estética & Barbería Central', 'Av. 18 de Julio 1420', '099112233', 'contacto@barberiacentral.uy', '08:00:00', '19:00:00'),
                    (2, 'Taller Mecánico & Servicios Rápidos', 'Bvar. Artigas 3250', '098445566', 'info@tallerapido.uy', '09:00:00', '18:00:00');
                """);

                jdbcTemplate.execute("""
                    INSERT INTO personal (id, id_establecimiento, nombre, especialidad, costo_consulta, duracion_estandar_minutos, estado)
                    VALUES
                    (1, 1, 'Dra. Sofía Martínez', 'Estética Facial', 1500.00, 30, 'ACTIVO'),
                    (2, 1, 'Carlos Gómez', 'Barbero / Estilista', 800.00, 30, 'ACTIVO'),
                    (3, 2, 'Martín Rodríguez', 'Mecánica General', 2200.00, 30, 'ACTIVO'),
                    (4, 1, 'Lucía Fernández', 'Cosmetología', 1200.00, 30, 'INACTIVO'),
                    (8, 1, 'Juan Pérez', 'Peluquería', 900.00, 30, 'ACTIVO');
                """);
                logger.info("datos iniciales cargados exitosamente");
            }

            logger.info("esquema y datos verificados correctamente");
        } catch (Exception e) {
            logger.error("error al inicializar esquema: {}", e.getMessage(), e);
        }
    }
}
