package com.laboratorio.turnos.validador.mqtt;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laboratorio.turnos.validador.service.ColaValidacion;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class ValidadorMqtt implements MqttCallbackExtended {

    private static final Logger logger =
            LoggerFactory.getLogger(ValidadorMqtt.class);

    private final ColaValidacion cola;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${MQTT_BROKER_URL:tcp://mosquitto:1883}")
    private String brokerUrl;

    @Value("${MQTT_TOPIC:turnos/reservas}")
    private String topic;

    private MqttClient cliente;
    private volatile boolean listo;

    public ValidadorMqtt(ColaValidacion cola) {
        this.cola = cola;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void iniciar() {
        listo = true;
    }

    // Conectar despues de inicializar la aplicacion
    @Scheduled(fixedDelay = 5000)
    public void conectar() {
        if (!listo) {
            return;
        }

        try {
            if (cliente == null) {
                cliente = new MqttClient(
                        brokerUrl,
                        "TurnoValidador",
                        new MemoryPersistence()
                );
                cliente.setCallback(this);
            }

            if (!cliente.isConnected()) {
                MqttConnectOptions opciones = new MqttConnectOptions();
                opciones.setCleanSession(true);
                opciones.setConnectionTimeout(3);
                cliente.connect(opciones);
            }
        } catch (MqttException e) {
            logger.warn("No se pudo conectar a MQTT: {}", e.getMessage());
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        try {
            cliente.subscribe(topic, 1);
            logger.info("Validador suscrito a {}", topic);
        } catch (MqttException e) {
            logger.error("No se pudo suscribir a MQTT", e);
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage mensaje)
            throws Exception {

        String contenido = new String(
                mensaje.getPayload(),
                StandardCharsets.UTF_8
        );

        JsonNode evento = mapper.readTree(contenido);
        String tipo = evento.path("status").asText();

        if (!"NUEVO".equals(tipo) && !"ACTUALIZAR".equals(tipo)) {
            return;
        }

        JsonNode id = evento.path("turno").path("id");

        if (!id.canConvertToInt() || id.asInt() <= 0) {
            logger.warn("Evento recibido sin un id de reserva valido");
            return;
        }

        cola.agregar(id.asInt());
        logger.info("Reserva {} recibida para validar", id.asInt());
    }

    @Override
    public void connectionLost(Throwable causa) {
        logger.warn("Conexion MQTT perdida");
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    public void publicarTurnoAtendido(Integer idReserva)
            throws MqttException {

        if (cliente == null || !cliente.isConnected()) {
            throw new MqttException(
                    MqttException.REASON_CODE_CLIENT_NOT_CONNECTED
            );
        }

        var evento = mapper.createObjectNode();
        evento.put("tipo", "TURNO_ATENDIDO");
        evento.put("idReserva", idReserva);

        MqttMessage mensaje = new MqttMessage(
                evento.toString().getBytes(StandardCharsets.UTF_8)
        );
        mensaje.setQos(1);

        cliente.publish("turnos/atendidos", mensaje);

        logger.info(
                "Evento de turno atendido publicado: reserva {}",
                idReserva
        );
    }

    @PreDestroy
    public void cerrar() throws MqttException {
        listo = false;

        if (cliente != null) {
            if (cliente.isConnected()) {
                cliente.disconnect();
            }
            cliente.close();
        }
    }
}