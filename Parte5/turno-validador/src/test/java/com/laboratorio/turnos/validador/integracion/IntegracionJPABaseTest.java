package com.laboratorio.turnos.validador.integracion;

import com.laboratorio.turnos.validador.config.DatabaseInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "validador.intervalo-ms=86400000"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class IntegracionJPABaseTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", IntegracionConectarBDTest.MARIADB::getJdbcUrl);
        registry.add("spring.datasource.username", IntegracionConectarBDTest.MARIADB::getUsername);
        registry.add("spring.datasource.password", IntegracionConectarBDTest.MARIADB::getPassword);
    }

    @BeforeAll
    static void crearEsquema() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                IntegracionConectarBDTest.MARIADB.getJdbcUrl(),
                IntegracionConectarBDTest.MARIADB.getUsername(),
                IntegracionConectarBDTest.MARIADB.getPassword()));
        new DatabaseInitializer(jdbc).run();
    }

    @Autowired
    protected JdbcTemplate jdbc;
}