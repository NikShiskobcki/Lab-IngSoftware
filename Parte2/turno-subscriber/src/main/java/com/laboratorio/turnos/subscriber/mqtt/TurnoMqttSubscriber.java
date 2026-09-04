package com.laboratorio.turnos.subscriber.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.laboratorio.turnos.subscriber.db.DatabaseManager;
import com.laboratorio.turnos.subscriber.model.ReservaMessageDTO;
import com.laboratorio.turnos.subscriber.model.TurnoDTO;
import com.laboratorio.turnos.subscriber.repository.ReservaTurnoRepository;
import com.laboratorio.turnos.subscriber.service.TurnoValidator;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;

/**
 * Suscriptor MQTT que escucha solicitudes de reserva de turnos,
 * valida las reglas del dominio y las persiste en MariaDB.
 */
public class TurnoMqttSubscriber implements MqttCallbackExtended, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(TurnoMqttSubscriber.class);

    private final String brokerUrl;
    private final String topic;
    private final String clientId;
    private final DatabaseManager databaseManager;
    private final TurnoValidator validator;
    private final ReservaTurnoRepository repository;
    private final ObjectMapper objectMapper;
    private MqttClient mqttClient;

    public TurnoMqttSubscriber(String brokerUrl, String topic, DatabaseManager databaseManager) {
        this.brokerUrl = brokerUrl;
        this.topic = topic;
        this.databaseManager = databaseManager;
        this.clientId = "TurnoSubscriber-" + System.currentTimeMillis();
        this.validator = new TurnoValidator();
        this.repository = new ReservaTurnoRepository();
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
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        logger.info("--> [EVENTO RECIBIDO] Tópico: {}, Tamaño: {} bytes", topic, payload.length());
        logger.debug("Payload JSON: {}", payload);

        try {
            ReservaMessageDTO mensajeDTO = objectMapper.readValue(payload, ReservaMessageDTO.class);
            TurnoDTO turno = mensajeDTO.getTurno();

            if (turno == null) {
                logger.warn("[DESCARTADO] El mensaje recibido no contiene la estructura del turno.");
                return;
            }

            // Validar y persistir usando conexión a MariaDB
            try (Connection conn = databaseManager.getConnection()) {
                TurnoValidator.ValidationResult result = validator.validate(turno, conn);

                if (result.isValid()) {
                    int idGenerado = repository.saveReserva(turno, mensajeDTO.getFechaHora(), conn);
                    logger.info("✓ [APROBADO & GUARDADO] Reserva ID_BD={} para '{}' en '{}'. Fecha: {}, Hora: {}, Cliente: <{}>",
                            idGenerado,
                            result.getNombreProfesional(),
                            result.getNombreEstablecimiento(),
                            turno.getFecha(),
                            turno.getHora(),
                            turno.getEmailCliente());
                } else {
                    logger.warn("✗ [RECHAZADO] {}", result.getReason());
                }
            }

        } catch (Exception e) {
            logger.error("Error al procesar el mensaje MQTT: {}", e.getMessage(), e);
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
