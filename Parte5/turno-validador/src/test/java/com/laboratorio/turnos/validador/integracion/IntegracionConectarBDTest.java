package com.laboratorio.turnos.validador.integracion;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MariaDBContainer;

public abstract class IntegracionConectarBDTest {

    protected static final MariaDBContainer<?> MARIADB =
            new MariaDBContainer<>("mariadb:11")
                    .withDatabaseName("reservas")
                    .withUsername("reservas_app")
                    .withPassword("admin");

    static {
        MARIADB.start();
    }

    protected JdbcTemplate jdbc;

    @BeforeEach
    void conectarYLimpiar() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                MARIADB.getJdbcUrl(), MARIADB.getUsername(), MARIADB.getPassword()));

        jdbc.execute("DROP TABLE IF EXISTS reservas_turnos");
        jdbc.execute("DROP TABLE IF EXISTS personal");
        jdbc.execute("DROP TABLE IF EXISTS establecimientos");
    }
}