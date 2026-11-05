package com.laboratorio.turnos.api.http;

import com.laboratorio.turnos.api.controller.ReservaController;
import com.laboratorio.turnos.api.establecimiento.EstablecimientoController;
import com.laboratorio.turnos.api.establecimiento.EstablecimientoRepository;
import com.laboratorio.turnos.api.exception.ApiExceptionHandler;
import com.laboratorio.turnos.api.personal.PersonalConsultaService;
import com.laboratorio.turnos.api.personal.PersonalController;
import com.laboratorio.turnos.api.personal.PersonalRepository;
import com.laboratorio.turnos.api.repository.ReservaRepository;
import com.laboratorio.turnos.api.service.MqttPublisherService;
import com.laboratorio.turnos.api.service.ReservaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Errores de cliente que NO dependen de la logica de negocio: JSON mal formado,
 * parametros faltantes o con tipo incorrecto, metodo HTTP no soportado y tipo de
 * contenido no soportado. El contrato esperado es 4xx (400 / 405 / 415), nunca 500.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HTTP | Errores de cliente transversales (4xx esperados)")
class CodigosHttpErroresTransversalesTest {

    @Mock private ReservaRepository reservaRepository;
    @Mock private MqttPublisherService mqttPublisherService;
    @Mock private EstablecimientoRepository establecimientoRepository;
    @Mock private PersonalRepository personalRepository;

    private MockMvc mvc;

    @BeforeEach
    void setUp(TestInfo info) {
        ReservaService reservaService = new ReservaService(reservaRepository, mqttPublisherService);
        PersonalConsultaService consultaService =
                new PersonalConsultaService(personalRepository, reservaRepository, establecimientoRepository);
        mvc = MockMvcBuilders.standaloneSetup(
                        new ReservaController(reservaService),
                        new EstablecimientoController(establecimientoRepository),
                        new PersonalController(personalRepository, establecimientoRepository, consultaService))
                .setControllerAdvice(new ApiExceptionHandler())
                .alwaysDo(RegistroHttp.registrar(info.getDisplayName()))
                .build();
    }

    @Test
    @DisplayName("CP-HTTP-43 | POST /reservas con JSON mal formado -> 400 Bad Request")
    void jsonMalFormado400() throws Exception {
        mvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON).content("{ esto no es json"))
                .andExpect(status().isBadRequest());
        verify(mqttPublisherService, never()).publicarReserva(any());
    }

    @Test
    @DisplayName("CP-HTTP-44 | POST /reservas con fecha en formato invalido -> 400 Bad Request")
    void fechaInvalida400() throws Exception {
        String json = """
                {"idPersonal":1,"idEstablecimiento":1,"emailCliente":"a@b.com",
                 "telefonoCliente":"099","fecha":"15/10/2030","hora":"10:00"}""";
        mvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CP-HTTP-45 | GET /personal/{id}/agenda sin parametro fecha -> 400 Bad Request")
    void agendaSinFecha400() throws Exception {
        mvc.perform(get("/personal/1/agenda"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CP-HTTP-46 | GET /personal/{id}/disponibilidad con fecha invalida -> 400 Bad Request")
    void disponibilidadFechaInvalida400() throws Exception {
        mvc.perform(get("/personal/1/disponibilidad").param("fecha", "no-es-fecha"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CP-HTTP-47 | GET /reservas/{id} con id no numerico -> 400 Bad Request")
    void idNoNumerico400() throws Exception {
        mvc.perform(get("/reservas/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CP-HTTP-48 | PATCH /reservas/{id} (metodo no soportado) -> 405 Method Not Allowed")
    void metodoNoSoportado405() throws Exception {
        mvc.perform(patch("/reservas/1"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("CP-HTTP-49 | POST /reservas con Content-Type text/plain -> 415 Unsupported Media Type")
    void contentTypeNoSoportado415() throws Exception {
        mvc.perform(post("/reservas").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
