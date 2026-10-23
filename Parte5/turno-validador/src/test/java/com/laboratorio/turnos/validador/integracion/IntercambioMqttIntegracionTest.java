package com.laboratorio.turnos.validador.integracion;

import com.laboratorio.turnos.validador.mqtt.ValidadorMqtt;
import com.laboratorio.turnos.validador.service.ColaValidacion;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.images.builder.Transferable;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@DisplayName("Intercambio de eventos MQTT")
class IntercambioMqttIntegracionTest {

    private static final String TOPICO = "turnos/reservas";

    @Container
    static final GenericContainer<?> mosquitto =
            new GenericContainer<>("eclipse-mosquitto:2")
                    .withExposedPorts(1883)
                    .withCopyToContainer(
                            Transferable.of(
                                    "listener 1883\nallow_anonymous true\n",
                                    0644
                            ),
                            "/mosquitto/config/mosquitto.conf"
                    );

    private ColaValidacion cola;
    private ValidadorMqtt consumidor;
    private MqttClient publicador;

    @BeforeEach
    void preparar() throws Exception {
        String broker = "tcp://" + mosquitto.getHost()
                + ":" + mosquitto.getMappedPort(1883);

        cola = new ColaValidacion();

        CountDownLatch suscrito = new CountDownLatch(1);

        consumidor = new ValidadorMqtt(cola) {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                super.connectComplete(reconnect, serverURI);
                suscrito.countDown();
            }
        };

        ReflectionTestUtils.setField(consumidor, "brokerUrl", broker);
        ReflectionTestUtils.setField(consumidor, "topic", TOPICO);

        consumidor.iniciar();
        consumidor.conectar();

        assertTrue(
                suscrito.await(10, TimeUnit.SECONDS),
                "El consumidor debe terminar de suscribirse antes de publicar"
        );

        publicador = new MqttClient(
                broker,
                MqttClient.generateClientId(),
                new MemoryPersistence()
        );
        publicador.connect();
    }

    @AfterEach
    void cerrar() throws Exception {
        try {
            if (publicador != null) {
                try {
                    if (publicador.isConnected()) {
                        publicador.disconnect();
                    }
                } finally {
                    publicador.close();
                }
            }
        } finally {
            if (consumidor != null) {
                consumidor.cerrar();
            }
        }
    }

    @Test
    @DisplayName("Un evento NUEVO valido llega a la cola de validacion")
    void eventoValidoEsRecibidoYProcesado() throws Exception {
        publicar("""
                {
                  "status": "NUEVO",
                  "turno": {
                    "id": 42,
                    "idPersonal": 1,
                    "idEstablecimiento": 1
                  }
                }
                """);

        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        assertEquals(Integer.valueOf(42), cola.siguiente())
                );

        assertNull(
                cola.siguiente(),
                "No debe haber otro evento pendiente"
        );
    }

    @Test
    @DisplayName("Un mensaje mal formado no impide recibir el siguiente evento")
    void mensajeMalFormadoNoRompeAlConsumidor() throws Exception {
        publicar("{\"status\":\"NUEVO\",\"turno\":");

        publicar("""
            {
              "status": "NUEVO",
              "turno": {
                "id": 43,
                "idPersonal": 1,
                "idEstablecimiento": 1
              }
            }
            """);

        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        assertEquals(Integer.valueOf(43), cola.siguiente())
                );

        assertNull(
                cola.siguiente(),
                "El mensaje mal formado no debe agregar IDs a la cola"
        );
    }

    private void publicar(String contenido) throws Exception {
        MqttMessage mensaje = new MqttMessage(
                contenido.getBytes(StandardCharsets.UTF_8)
        );
        mensaje.setQos(1);

        publicador.publish(TOPICO, mensaje);
    }
}