package com.laboratorio.turnos.facturacion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// ejecuta el proceso de facturacion de forma periodica.
// el scheduler de spring usa un solo hilo, por lo que las ejecuciones no se superponen.
@Component
public class FacturacionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(FacturacionScheduler.class);

    private final FacturacionService service;

    public FacturacionScheduler(FacturacionService service) {
        this.service = service;
    }

    @Scheduled(fixedRateString = "${facturacion.intervalo-ms}",
            initialDelayString = "${facturacion.delay-inicial-ms}")
    public void ejecutar() {
        try {
            service.ejecutarCiclo();
        } catch (Throwable t) {
            logger.error("error inesperado en el ciclo de facturacion: {}", t.getMessage(), t);
        }
    }
}
