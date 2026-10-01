package com.laboratorio.turnos.facturacion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// ejecuta el proceso de facturacion de forma periodica y sin superposicion
public class FacturacionScheduler implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(FacturacionScheduler.class);

    private final FacturacionService service;
    private final long intervaloMs;
    private final long delayInicialMs;
    private final AtomicBoolean enCurso = new AtomicBoolean(false);
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "facturacion-scheduler");
        t.setDaemon(false);
        return t;
    });

    public FacturacionScheduler(FacturacionService service, long intervaloMs, long delayInicialMs) {
        this.service = service;
        this.intervaloMs = intervaloMs;
        this.delayInicialMs = delayInicialMs;
    }

    public void start() {
        logger.info("proceso de facturacion programado cada {} ms (primera ejecucion en {} ms)", intervaloMs, delayInicialMs);
        executor.scheduleAtFixedRate(this::ejecutarSeguro, delayInicialMs, intervaloMs, TimeUnit.MILLISECONDS);
    }

    // cualquier excepcion se captura: si escapara, el executor cancelaria las ejecuciones siguientes
    private void ejecutarSeguro() {
        if (!enCurso.compareAndSet(false, true)) {
            logger.warn("la ejecucion anterior sigue en curso, se omite esta");
            return;
        }
        try {
            service.ejecutarCiclo();
        } catch (Throwable t) {
            logger.error("error inesperado en el ciclo de facturacion: {}", t.getMessage(), t);
        } finally {
            enCurso.set(false);
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
