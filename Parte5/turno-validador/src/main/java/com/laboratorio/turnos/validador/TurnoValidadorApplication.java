package com.laboratorio.turnos.validador;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// aplicacion principal del servicio validador de turnos
@SpringBootApplication
@EnableScheduling
public class TurnoValidadorApplication {

    public static void main(String[] args) {
        SpringApplication.run(TurnoValidadorApplication.class, args);
    }
}
