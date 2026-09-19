package com.laboratorio.turnos.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class TurnosApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TurnosApiApplication.class, args);
    }

    //endpoint temporal
    @RestController
    static class HealthController {
        @GetMapping("/health")
        public String health() {
            return "turnos-api OK";
        }
    }
}