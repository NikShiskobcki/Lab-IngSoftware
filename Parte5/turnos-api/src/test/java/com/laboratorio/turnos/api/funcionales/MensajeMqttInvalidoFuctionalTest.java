package com.laboratorio.turnos.api.funcionales;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("US-66 | Mensaje MQTT invalido en el sistema completo")
class MensajeMqttInvalidoFunctionalTest {

    private static final String API = "http://localhost:8080";
    private static final String BROKER = "tcp://localhost:1883";
    private static final String TOPICO = "turnos/reservas";

    private final ObjectMapper mapper = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    @DisplayName("Un mensaje invalido no altera reservas ni impide procesar otra")
    void mensajeInvalidoNoAlteraDatosYPermiteReservar() throws Exception {
        String identificador = UUID.randomUUID().toString();

        JsonNode establecimiento = post(
                "/establecimientos",
                Map.of(
                        "nombreComercial", "Clinica US66",
                        "direccion", "Calle Prueba 123",
                        "telefono", "29001234",
                        "correoElectronico", "clinica@correo.com",
                        "horarioApertura", "08:00",
                        "horarioCierre", "18:00"
                ),
                201
        );

        int idEstablecimiento = establecimiento.required("id").asInt();

        JsonNode personal = post(
                "/personal",
                Map.of(
                        "idEstablecimiento", idEstablecimiento,
                        "nombre", "Profesional US66",
                        "especialidad", "Consulta",
                        "costoConsulta", 1500,
                        "duracionEstandarMinutos", 30,
                        "estado", "ACTIVO"
                ),
                201
        );

        int idPersonal = personal.required("id").asInt();

        // Reserva de referencia para comprobar que conserva sus datos.
        JsonNode referencia = crearReserva(
                idPersonal,
                idEstablecimiento,
                "referencia." + identificador + "@correo.com",
                "10:00"
        );

        int idReferencia = referencia.required("datos")
                .required("id").asInt();

        esperarAgendado(idReferencia);

        Map<Integer, JsonNode> antes = consultarReservas();

        publicarMensajeInvalido();

        // Dar tiempo al consumidor para recibir el mensaje.
        Thread.sleep(2000);

        Map<Integer, JsonNode> despues = consultarReservas();

        assertEquals(
                antes,
                despues,
                "El mensaje invalido no debe crear, eliminar ni modificar reservas"
        );

        JsonNode respuesta = crearReserva(
                idPersonal,
                idEstablecimiento,
                "posterior." + identificador + "@correo.com",
                "11:00"
        );

        int idPosterior = respuesta.required("datos")
                .required("id").asInt();

        esperarAgendado(idPosterior);

        assertEquals(
                antes.get(idReferencia),
                get("/reservas/" + idReferencia),
                "La reserva de referencia debe conservar sus datos"
        );
    }

    private JsonNode crearReserva(
            int idPersonal,
            int idEstablecimiento,
            String email,
            String hora
    ) throws Exception {
        return post(
                "/reservas",
                Map.of(
                        "idPersonal", idPersonal,
                        "idEstablecimiento", idEstablecimiento,
                        "emailCliente", email,
                        "telefonoCliente", "099123456",
                        "fecha", LocalDate.now().plusDays(2).toString(),
                        "hora", hora
                ),
                201
        );
    }

    private void esperarAgendado(int idReserva) {
        await()
                .pollInterval(Duration.ofSeconds(2))
                .atMost(Duration.ofSeconds(90))
                .untilAsserted(() ->
                        assertEquals(
                                "Agendado",
                                get("/reservas/" + idReserva)
                                        .path("estado").asText(),
                                "La reserva debe ser procesada por el validador"
                        )
                );
    }

    private void publicarMensajeInvalido() throws Exception {
        MqttClient cliente = new MqttClient(
                BROKER,
                MqttClient.generateClientId(),
                new MemoryPersistence()
        );

        try {
            cliente.connect();

            MqttMessage mensaje = new MqttMessage(
                    "{\"status\":\"NUEVO\",\"turno\":"
                            .getBytes(StandardCharsets.UTF_8)
            );
            mensaje.setQos(1);

            cliente.publish(TOPICO, mensaje);
        } finally {
            try {
                if (cliente.isConnected()) {
                    cliente.disconnect();
                }
            } finally {
                cliente.close();
            }
        }
    }

    private Map<Integer, JsonNode> consultarReservas() throws Exception {
        JsonNode reservas = get("/reservas");

        assertTrue(
                reservas.isArray(),
                "GET /reservas debe devolver una lista"
        );

        Map<Integer, JsonNode> resultado = new TreeMap<>();

        for (JsonNode reserva : reservas) {
            resultado.put(reserva.required("id").asInt(), reserva);
        }

        return resultado;
    }

    private JsonNode get(String ruta) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder()
                .uri(URI.create(API + ruta))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> respuesta = http.send(
                peticion,
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(
                200,
                respuesta.statusCode(),
                "GET " + ruta + ": " + respuesta.body()
        );

        return mapper.readTree(respuesta.body());
    }

    private JsonNode post(
            String ruta,
            Map<String, Object> datos,
            int codigoEsperado
    ) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder()
                .uri(URI.create(API + ruta))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(datos)
                ))
                .build();

        HttpResponse<String> respuesta = http.send(
                peticion,
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(
                codigoEsperado,
                respuesta.statusCode(),
                "POST " + ruta + ": " + respuesta.body()
        );

        return mapper.readTree(respuesta.body());
    }
}
