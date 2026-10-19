package com.laboratorio.turnos.subscriber.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;



public class TurnoMqttSubscriber implements MqttCallbackExtended, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(TurnoMqttSubscriber.class);

    private final String brokerUrl;
    private final String topic;
    private final String clientId;
    private final ObjectMapper objectMapper;
    private MqttClient mqttClient;

    public TurnoMqttSubscriber(String brokerUrl, String topic) {
        this.brokerUrl = brokerUrl;
        this.topic = topic;
        this.clientId = "TurnoSubscriber-" + System.currentTimeMillis();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void start() throws MqttException {
        mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        mqttClient.setCallback(this);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(false); // Mantener sesión para no perder mensajes en reinicios breves
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);

        logger.info("Iniciando conexión del suscriptor a {} con clientId={}...", brokerUrl, clientId);
        mqttClient.connect(options);
        logger.info("Suscriptor conectado exitosamente.");
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        logger.info("Conexión MQTT establecida (reconnect={}). Suscribiendo a tópico [{}]...", reconnect, topic);
        try {
            mqttClient.subscribe(topic, 1);
            logger.info("Suscripción activa en el tópico [{}] con QoS 1.", topic);
        } catch (MqttException e) {
            logger.error("Error al suscribirse al tópico {}: {}", topic, e.getMessage(), e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        logger.warn("Conexión MQTT perdida: {}. Se intentará reconectar automáticamente...",
                cause != null ? cause.getMessage() : "Desconocido");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(
                message.getPayload(),
                StandardCharsets.UTF_8
        );

        try {
            var evento = objectMapper.readTree(payload);
            var turno = evento.get("turno");

            if (turno == null || !turno.hasNonNull("id")) {
                logger.warn("Evento recibido sin id de reserva.");
                return;
            }

            String tipo = evento.path("status").asText();
            int idReserva = turno.get("id").asInt();

            logger.info(
                    "Evento MQTT recibido: tipo={}, reserva={}, establecimiento={}, personal={}",
                    tipo,
                    idReserva,
                    turno.path("idEstablecimiento").asInt(),
                    turno.path("idPersonal").asInt()
            );

        } catch (Exception e) {
            logger.error("Error al leer el evento MQTT", e);
        }
    }


    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // No aplica para el suscriptor
    }

    @Override
    public void close() {
        if (mqttClient != null) {
            try {
                if (mqttClient.isConnected()) {
                    mqttClient.disconnect();
                }
                mqttClient.close();
                logger.info("Suscriptor MQTT cerrado correctamente.");
            } catch (MqttException e) {
                logger.error("Error al cerrar suscriptor MQTT", e);
            }
        }
    }
}
