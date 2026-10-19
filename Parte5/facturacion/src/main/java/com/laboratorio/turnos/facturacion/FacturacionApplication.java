package com.laboratorio.turnos.facturacion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// servicio asincrono de facturacion de turnos atendidos (us-40, us-41, us-42)
@SpringBootApplication
@EnableScheduling
public class FacturacionApplication {

    public static void main(String[] args) {
        SpringApplication.run(FacturacionApplication.class, args);
    }
}
