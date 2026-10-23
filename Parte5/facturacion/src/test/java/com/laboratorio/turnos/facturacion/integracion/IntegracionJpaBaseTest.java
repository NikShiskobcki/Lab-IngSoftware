package com.laboratorio.turnos.facturacion.integracion;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;

// hibernate (ddl-auto=update) crea clientes, facturas e items_factura;
// personal y reservas_turnos (de otro servicio) las crea el script esquema-externo.sql
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=update")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class IntegracionJpaBaseTest {

    protected static final MariaDBContainer<?> MARIADB =
            new MariaDBContainer<>("mariadb:11")
                    .withDatabaseName("reservas")
                    .withUsername("reservas_app")
                    .withPassword("admin")
                    .withInitScript("esquema-externo.sql");

    static {
        MARIADB.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MARIADB::getJdbcUrl);
        registry.add("spring.datasource.username", MARIADB::getUsername);
        registry.add("spring.datasource.password", MARIADB::getPassword);
    }

    @Autowired
    protected JdbcTemplate jdbc;
}