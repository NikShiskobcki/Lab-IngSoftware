package com.laboratorio.turnos.subscriber;

import com.laboratorio.turnos.subscriber.db.DatabaseManager;
import com.laboratorio.turnos.subscriber.mqtt.TurnoMqttSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Punto de entrada principal para el componente Consumidor/Suscriptor de Turnos.
 * Inicializa la base de datos MariaDB, conecta el suscriptor MQTT y persiste las reservas.
 */
public class SubscriberApp {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberApp.class);

    public static void main(String[] args) {
        logger.info("=== Iniciando Consumidor y Persistencia de Turnos (MQTT Subscriber) ===");

        String brokerUrl = getEnv("MQTT_BROKER_URL", "tcp://mosquitto:1883");
        String topic = getEnv("MQTT_TOPIC", "turnos/reservas");
        String dbUrl = getEnv("DB_URL", "jdbc:mariadb://mariadb:3306/reservas");
        String dbUser = getEnv("DB_USER", "reservas_app");
        String dbPassword = getEnv("DB_PASSWORD", "admin");

        logger.info("Configuración: Broker={}, Tópico={}, DB_URL={}", brokerUrl, topic, dbUrl);

        // 1. Inicializar y verificar conexión con MariaDB
        DatabaseManager databaseManager = new DatabaseManager(dbUrl, dbUser, dbPassword);
        databaseManager.waitAndInitialize();

        // 2. Iniciar el suscriptor MQTT
        TurnoMqttSubscriber subscriber = new TurnoMqttSubscriber(brokerUrl, topic, databaseManager);

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
            databaseManager.close();
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
