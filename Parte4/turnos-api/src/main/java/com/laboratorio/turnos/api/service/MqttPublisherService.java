package com.laboratorio.turnos.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.laboratorio.turnos.api.dto.mqtt.MqttReservaMessageDTO;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

// servicio para publicar mensajes en el broker mqtt mosquitto
@Service
public class MqttPublisherService {

    private static final Logger logger = LoggerFactory.getLogger(MqttPublisherService.class);

    @Value("${mqtt.broker.url:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${mqtt.topic:turnos/reservas}")
    private String topic;

    private final ObjectMapper objectMapper;

    public MqttPublisherService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void publicarReserva(MqttReservaMessageDTO mensajeDTO) {
        String clientId = "TurnosApiPublisher-" + System.currentTimeMillis();
        try (MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence())) {
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            connOpts.setConnectionTimeout(10);

            logger.info("conectando a broker mqtt {}", brokerUrl);
            client.connect(connOpts);

            String jsonPayload = objectMapper.writeValueAsString(mensajeDTO);
            MqttMessage message = new MqttMessage(jsonPayload.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);

            logger.info("publicando mensaje en topico {}", topic);
            client.publish(topic, message);
            client.disconnect();
            logger.info("mensaje publicado exitosamente");
        } catch (Exception e) {
            logger.error("error al publicar mensaje mqtt: {}", e.getMessage());
            throw new RuntimeException("error al conectar o publicar en mqtt: " + e.getMessage(), e);
        }
    }
}
