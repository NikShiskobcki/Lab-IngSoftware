package com.laboratorio.turnos.validador.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// inicializador para compatibilidad de columnas en base de datos
@Component
public class DatabaseInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            // asegurar que la columna opcional id_establecimiento exista en reservas_turnos
            jdbcTemplate.execute("ALTER TABLE reservas_turnos ADD COLUMN IF NOT EXISTS id_establecimiento INT NULL;");
            logger.info("verificacion de esquema de reservas_turnos completada");
        } catch (Exception e) {
            logger.warn("aviso al verificar columnas: {}", e.getMessage());
        }
    }
}
