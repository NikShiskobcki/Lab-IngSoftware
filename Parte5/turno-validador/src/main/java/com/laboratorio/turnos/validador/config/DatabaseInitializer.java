package com.laboratorio.turnos.validador.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

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
            Files.deleteIfExists(Path.of("/tmp/esquema-listo"));
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

            // actualizar el esquema si la tabla ya existia
            jdbcTemplate.execute("""
                        ALTER TABLE reservas_turnos
                        ADD COLUMN IF NOT EXISTS id_establecimiento INT NULL;
                    """);

            jdbcTemplate.execute("""
                        ALTER TABLE reservas_turnos
                        MODIFY COLUMN estado VARCHAR(50) NOT NULL DEFAULT 'SOLICITADO';
                    """);

            jdbcTemplate.execute("""
                        ALTER TABLE reservas_turnos
                        DROP INDEX IF EXISTS uq_personal_fecha_hora;
                    """);

            Files.writeString(Path.of("/tmp/esquema-listo"), "listo");
            logger.info("esquema verificado correctamente");

        } catch (Exception e) {
            logger.error("error al inicializar esquema", e);
            throw new IllegalStateException("No se pudo preparar el esquema", e);
        }
    }
}

