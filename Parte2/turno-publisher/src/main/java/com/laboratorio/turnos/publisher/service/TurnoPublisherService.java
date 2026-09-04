package com.laboratorio.turnos.publisher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.laboratorio.turnos.publisher.model.ReservaMessageDTO;
import com.laboratorio.turnos.publisher.model.TurnoDTO;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Servicio encargado de publicar reservas de turnos en el broker MQTT.
 * 
 * NOTA DE ARQUITECTURA / ADAPTABILIDAD:
 * Esta clase está diseñada para ser fácilmente inyectada y reutilizada
 * en futuras entregas desde una API REST (por ejemplo Spring Boot o Jakarta RS),
 * simplemente invocando el método {@link #publishTurno(TurnoDTO)}.
 */
public class TurnoPublisherService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(TurnoPublisherService.class);

    private final String brokerUrl;
    private final String topic;
    private final String clientId;
    private final ObjectMapper objectMapper;
    private MqttClient mqttClient;

    public TurnoPublisherService(String brokerUrl, String topic) {
        this.brokerUrl = brokerUrl;
        this.topic = topic;
        this.clientId = "TurnoPublisher-" + System.currentTimeMillis();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Inicializa y conecta el cliente MQTT al broker con reintentos.
     */
    public synchronized void connect() throws MqttException {
        if (mqttClient != null && mqttClient.isConnected()) {
            return;
        }

        mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);

        logger.info("Conectando al broker MQTT en {} con clientId={}...", brokerUrl, clientId);
        mqttClient.connect(options);
        logger.info("Conectado exitosamente al broker MQTT!");
    }

    /**
     * Publica un turno recibido como parámetro en el tópico MQTT.
     * Este es el método central que consumirá la futura API REST.
     *
     * @param turno Datos del turno solicitado.
     * @return El mensaje completo publicado.
     * @throws Exception Si ocurre un error al serializar o publicar en MQTT.
     */
    public synchronized ReservaMessageDTO publishTurno(TurnoDTO turno) throws Exception {
        if (mqttClient == null || !mqttClient.isConnected()) {
            logger.warn("Cliente MQTT desconectado. Intentando reconectar...");
            connect();
        }

        ReservaMessageDTO mensaje = new ReservaMessageDTO(
                "turno_creado",
                LocalDateTime.now(),
                turno
        );

        String jsonPayload = objectMapper.writeValueAsString(mensaje);
        MqttMessage mqttMessage = new MqttMessage(jsonPayload.getBytes(StandardCharsets.UTF_8));
        mqttMessage.setQos(1);

        logger.info("Publicando turno en tópico [{}]: {}", topic, jsonPayload);
        mqttClient.publish(topic, mqttMessage);
        logger.info("Mensaje publicado exitosamente.");

        return mensaje;
    }

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    @Override
    public synchronized void close() {
        if (mqttClient != null) {
            try {
                if (mqttClient.isConnected()) {
                    mqttClient.disconnect();
                }
                mqttClient.close();
                logger.info("Cliente MQTT desconectado y cerrado correctamente.");
            } catch (MqttException e) {
                logger.error("Error al cerrar conexión MQTT", e);
            }
        }
    }
}
