package com.laboratorio.turnos.subscriber;

import com.laboratorio.turnos.subscriber.db.DatabaseManager;
import com.laboratorio.turnos.subscriber.mqtt.TurnoMqttSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


// Punto de entrada principal para el suscriptor de Turnos.

public class SubscriberApp {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberApp.class);

    public static void main(String[] args) {
        logger.info("Iniciando consumidor de eventos MQTT");

        String brokerUrl = getEnv("MQTT_BROKER_URL", "tcp://mosquitto:1883");
        String topic = getEnv("MQTT_TOPIC", "turnos/reservas");

        logger.info("Configuracion: Broker={}, Topico={}", brokerUrl, topic);

        TurnoMqttSubscriber subscriber = new TurnoMqttSubscriber(brokerUrl, topic);

        boolean connected = false;
        while (!connected) {
            try {
                subscriber.start();
                connected = true;
            } catch (Exception e) {
                logger.warn("Esperando disponibilidad del broker MQTT: {}. Reintentando en 3 segundos...", e.getMessage());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        // Cierre ordenado
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Deteniendo Subscriber...");
            subscriber.close();
            logger.info("Subscriber detenido correctamente.");
        }));

        logger.info("Consumidor listo y a la espera de solicitudes en [{}]...", topic);

        // Mantener vivo el proceso principal
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            logger.info("Hilo principal interrumpido.");
            Thread.currentThread().interrupt();
        }
    }

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
