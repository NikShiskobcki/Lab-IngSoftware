package com.laboratorio.turnos.facturacion.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laboratorio.turnos.facturacion.service.ColaFacturacion;
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
public class FacturacionMqtt implements MqttCallbackExtended {

    private static final Logger logger =
            LoggerFactory.getLogger(FacturacionMqtt.class);

    private final ColaFacturacion cola;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${MQTT_BROKER_URL:tcp://mosquitto:1883}")
    private String brokerUrl;

    private MqttClient cliente;
    private volatile boolean listo;

    public FacturacionMqtt(ColaFacturacion cola) {
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
                        "Facturacion",
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
            cliente.subscribe("turnos/atendidos", 1);
            logger.info("Facturacion suscrita a turnos/atendidos");
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

        if (!"TURNO_ATENDIDO".equals(evento.path("tipo").asText())) {
            return;
        }

        JsonNode id = evento.path("idReserva");

        if (!id.canConvertToInt() || id.asInt() <= 0) {
            logger.warn("Evento recibido sin un id de reserva valido");
            return;
        }

        cola.agregar(id.asInt());
        logger.info("Reserva {} recibida para facturar", id.asInt());
    }

    @Override
    public void connectionLost(Throwable causa) {
        logger.warn("Conexion MQTT perdida");
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
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
