package com.laboratorio.turnos.api.funcionales;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;

@DisplayName("US-68 | Recuperacion de facturacion tras reinicio")
class RecuperacionFacturacionFunctionalTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final Path archivoCompose = buscarCompose();

    @Test
    @DisplayName("Un evento pendiente debe recuperarse despues del reinicio")
    void debeRecuperarFacturacionPendienteTrasReinicio() throws Exception {
        String inicioPrueba = Instant.now().toString();

        // Comenzar con un ciclo nuevo de facturacion.
        compose(
                "up", "-d",
                "--force-recreate",
                "--no-deps",
                "facturacion"
        );

        await()
                .pollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(90))
                .untilAsserted(() -> {
                    String logs = compose(
                            "logs",
                            "--no-color",
                            "--tail", "200",
                            "facturacion"
                    );

                    assertTrue(
                            logs.contains(
                                    "Facturacion suscrita a turnos/atendidos"
                            ),
                            "Facturacion debe estar suscrita"
                    );

                    assertTrue(
                            logs.contains("Facturacion finalizada:"),
                            "Debe terminar el primer ciclo de facturacion"
                    );
                });

        JsonNode establecimiento = post(
                "/establecimientos",
                Map.of(
                        "nombreComercial", "Clinica US68",
                        "direccion", "Calle Prueba 123",
                        "telefono", "29001234",
                        "correoElectronico", "clinica@correo.com",
                        "horarioApertura", "08:00",
                        "horarioCierre", "18:00"
                )
        );

        int idEstablecimiento =
                establecimiento.required("id").asInt();

        JsonNode personal = post(
                "/personal",
                Map.of(
                        "idEstablecimiento", idEstablecimiento,
                        "nombre", "Profesional US68",
                        "especialidad", "Consulta",
                        "costoConsulta", 1500,
                        "duracionEstandarMinutos", 30,
                        "estado", "ACTIVO"
                )
        );

        int idPersonal = personal.required("id").asInt();

        JsonNode respuesta = post(
                "/reservas",
                Map.of(
                        "idPersonal", idPersonal,
                        "idEstablecimiento", idEstablecimiento,
                        "emailCliente",
                        "reinicio." + UUID.randomUUID() + "@correo.com",
                        "telefonoCliente", "099123456",
                        "fecha", LocalDate.now().plusDays(2).toString(),
                        "hora", "10:00"
                )
        );

        int idReserva = respuesta.required("datos")
                .required("id").asInt();

        // Preparar un turno atendido como dato de prueba.
        sql("""
                UPDATE reservas_turnos
                SET estado = 'Atendido',
                    fecha_turno = DATE_SUB(CURDATE(), INTERVAL 1 DAY)
                WHERE id = %d
                """.formatted(idReserva));

        String evento = mapper.writeValueAsString(
                Map.of(
                        "tipo", "TURNO_ATENDIDO",
                        "idReserva", idReserva
                )
        );

        publicarEventoAtendido(evento);
        System.out.println("Evento publicado: " + evento);
        System.out.println("Compose utilizado: " + archivoCompose);

        await()
                .pollInterval(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    String logs = compose(
                            "logs",
                            "--no-color",
                            "--tail", "200",
                            "facturacion"
                    );

                    assertTrue(
                            logs.contains(
                                    "Reserva " + idReserva
                                            + " recibida para facturar"
                            ),
                            "No se encontro la recepcion del evento."
                                    + "\nEvento enviado: " + evento
                                    + "\nLogs consultados:\n" + logs
                    );
                });

        assertEquals(
                "Atendido",
                get("/reservas/" + idReserva)
                        .path("estado").asText(),
                "El turno debe seguir pendiente antes del reinicio"
        );

        assertEquals(
                "0",
                cantidadItems(idReserva),
                "No debe estar facturado antes del reinicio"
        );

        JsonNode antes = get("/reservas/" + idReserva);

        boolean requiereLevantar = true;

        try {
            // Conservar el volumen de MariaDB.
            compose("down");

            String inicioNuevoArranque = Instant.now().toString();

            compose("up", "-d");
            requiereLevantar = false;

            // Esperar a que la API vuelva a responder.
            await()
                    .pollInterval(Duration.ofSeconds(2))
                    .atMost(Duration.ofSeconds(90))
                    .ignoreExceptionsMatching(e -> e instanceof java.io.IOException)
                    .untilAsserted(() -> {
                        JsonNode despues = get("/reservas/" + idReserva);

                        assertEquals(
                                antes.path("id"),
                                despues.path("id")
                        );

                        assertEquals(
                                antes.path("emailSolicitante"),
                                despues.path("emailSolicitante")
                        );

                        assertEquals(
                                antes.path("idPersonal"),
                                despues.path("idPersonal")
                        );

                        assertEquals(
                                antes.path("idEstablecimiento"),
                                despues.path("idEstablecimiento")
                        );

                        assertEquals(
                                antes.path("fechaTurno"),
                                despues.path("fechaTurno")
                        );

                        assertEquals(
                                antes.path("horaTurno"),
                                despues.path("horaTurno")
                        );
                    });

            // Esperar un ciclo completo del nuevo proceso.
            await()
                    .pollInterval(Duration.ofSeconds(2))
                    .atMost(Duration.ofSeconds(90))
                    .untilAsserted(() -> {
                        String logs = compose(
                                "logs",
                                "--no-color",
                                "--tail", "200",
                                "facturacion"
                        );

                        assertTrue(
                                logs.contains("Facturacion finalizada:"),
                                "Debe finalizar un ciclo tras el reinicio"
                        );
                    });

            String estado = get("/reservas/" + idReserva)
                    .path("estado").asText();

            String items = cantidadItems(idReserva);

            System.out.println("Reserva: " + idReserva);
            System.out.println(
                    "Estado despues del reinicio: " + estado
            );
            System.out.println("Items asociados: " + items);

            assertAll(
                    () -> assertEquals(
                            "Facturado",
                            estado,
                            "El trabajo pendiente debe recuperarse"
                    ),
                    () -> assertEquals(
                            "1",
                            items,
                            "Debe existir un unico item para el turno"
                    )
            );
        } finally {
            if (requiereLevantar) {
                compose("up", "-d");
            }
        }
    }

    private void publicarEventoAtendido(String evento) throws Exception {
        Process proceso = new ProcessBuilder(
                "docker", "exec", "-i", "mosquitto",
                "mosquitto_pub",
                "-h", "127.0.0.1",
                "-p", "1883",
                "-t", "turnos/atendidos",
                "-q", "1",
                "-s"
        )
                .redirectErrorStream(true)
                .start();

        try (var entrada = proceso.getOutputStream()) {
            entrada.write(evento.getBytes(StandardCharsets.UTF_8));
        }

        boolean termino = proceso.waitFor(15, TimeUnit.SECONDS);

        if (!termino) {
            proceso.destroyForcibly();
            fail("Mosquitto no termino de publicar el evento");
        }

        String salida = new String(
                proceso.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );

        assertEquals(
                0,
                proceso.exitValue(),
                "No se pudo publicar el evento MQTT:\n" + salida
        );
    }

    private String cantidadItems(int idReserva) throws Exception {
        return sql(
                "SELECT COUNT(*) FROM items_factura WHERE id_turno = "
                        + idReserva
        ).trim();
    }

    private String sql(String consulta) throws Exception {
        return compose(
                "exec", "-T", "mariadb",
                "mariadb",
                "-u", "reservas_app",
                "-padmin",
                "--batch", "--skip-column-names",
                "reservas",
                "-e", consulta
        );
    }

    private JsonNode get(String ruta) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080" + ruta))
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
                respuesta.body()
        );

        return mapper.readTree(respuesta.body());
    }

    private JsonNode post(
            String ruta,
            Map<String, Object> datos
    ) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080" + ruta))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(
                        HttpRequest.BodyPublishers.ofString(
                                mapper.writeValueAsString(datos)
                        )
                )
                .build();

        HttpResponse<String> respuesta = http.send(
                peticion,
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(
                201,
                respuesta.statusCode(),
                respuesta.body()
        );

        return mapper.readTree(respuesta.body());
    }

    private String compose(String... argumentos) throws Exception {
        List<String> comando = new ArrayList<>(
                List.of(
                        "docker", "compose",
                        "-f", archivoCompose.toString()
                )
        );

        comando.addAll(Arrays.asList(argumentos));

        Path salida = Files.createTempFile("us68-", ".log");

        try {
            Process proceso = new ProcessBuilder(comando)
                    .redirectErrorStream(true)
                    .redirectOutput(salida.toFile())
                    .start();

            if (!proceso.waitFor(180, TimeUnit.SECONDS)) {
                proceso.destroyForcibly();

                fail("El comando Docker excedio el tiempo permitido");
            }

            String contenido = Files.readString(salida);

            assertEquals(
                    0,
                    proceso.exitValue(),
                    "Fallo Docker: " + contenido
            );

            return contenido;
        } finally {
            Files.deleteIfExists(salida);
        }
    }

    private static Path buscarCompose() {
        Path directorio = Path.of("").toAbsolutePath();

        while (directorio != null) {
            Path candidato =
                    directorio.resolve("Parte5/docker-compose.yml");

            if (Files.exists(candidato)) {
                return candidato;
            }

            if (directorio.getFileName() != null
                    && directorio.getFileName().toString()
                    .equals("Parte5")) {

                candidato =
                        directorio.resolve("docker-compose.yml");

                if (Files.exists(candidato)) {
                    return candidato;
                }
            }

            directorio = directorio.getParent();
        }

        throw new IllegalStateException(
                "No se encontro Parte5/docker-compose.yml"
        );
    }
}
