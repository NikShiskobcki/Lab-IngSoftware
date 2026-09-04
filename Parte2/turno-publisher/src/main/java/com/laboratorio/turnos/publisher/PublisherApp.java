package com.laboratorio.turnos.publisher;

import com.laboratorio.turnos.publisher.generator.RandomTurnoGenerator;
import com.laboratorio.turnos.publisher.model.TurnoDTO;
import com.laboratorio.turnos.publisher.service.TurnoPublisherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Aplicación principal del generador y publicador de turnos.
 * Ejecuta periódicamente la generación de un turno aleatorio y lo publica en MQTT.
 */
public class PublisherApp {

    private static final Logger logger = LoggerFactory.getLogger(PublisherApp.class);

    public static void main(String[] args) {
        logger.info("=== Iniciando Generador y Publicador de Turnos (MQTT Publisher) ===");

        String brokerUrl = getEnv("MQTT_BROKER_URL", "tcp://mosquitto:1883");
        String topic = getEnv("MQTT_TOPIC", "turnos/reservas");
        long intervalMs = Long.parseLong(getEnv("PUBLISH_INTERVAL_MS", "10000"));

        logger.info("Configuración: Broker={}, Tópico={}, Intervalo={}ms", brokerUrl, topic, intervalMs);

        TurnoPublisherService publisherService = new TurnoPublisherService(brokerUrl, topic);
        RandomTurnoGenerator generator = new RandomTurnoGenerator();

        // Conectar al broker con reintentos mientras Mosquitto inicia
        boolean connected = false;
        while (!connected) {
            try {
                publisherService.connect();
                connected = true;
            } catch (Exception e) {
                logger.warn("No se pudo conectar al broker MQTT: {}. Reintentando en 3 segundos...", e.getMessage());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        // Registrar cierre ordenado (graceful shutdown)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Deteniendo Publisher...");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
            publisherService.close();
            logger.info("Publisher detenido con éxito.");
        }));

        // Tarea periódica de generación y publicación
        scheduler.scheduleAtFixedRate(() -> {
            try {
                TurnoDTO turnoAleatorio = generator.generateRandomTurno();
                logger.info("Generando solicitud de reserva simulada: ID={}, Personal={}, Cliente={}, Fecha={}, Hora={}",
                        turnoAleatorio.getId(),
                        turnoAleatorio.getIdPersonal(),
                        turnoAleatorio.getEmailCliente(),
                        turnoAleatorio.getFecha(),
                        turnoAleatorio.getHora());

                publisherService.publishTurno(turnoAleatorio);

            } catch (Exception e) {
                logger.error("Error al generar y publicar turno: {}", e.getMessage(), e);
            }
        }, 2000, intervalMs, TimeUnit.MILLISECONDS);

        logger.info("Generador activo. Publicando eventos cada {} segundos...", intervalMs / 1000);
    }

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
