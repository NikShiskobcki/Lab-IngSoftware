package com.laboratorio.turnos.api.http;

import com.laboratorio.turnos.api.controller.ReservaController;
import com.laboratorio.turnos.api.establecimiento.Establecimiento;
import com.laboratorio.turnos.api.establecimiento.EstablecimientoController;
import com.laboratorio.turnos.api.establecimiento.EstablecimientoRepository;
import com.laboratorio.turnos.api.exception.ApiExceptionHandler;
import com.laboratorio.turnos.api.model.Reserva;
import com.laboratorio.turnos.api.personal.EstadoPersonal;
import com.laboratorio.turnos.api.personal.Personal;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas de codigos de estado HTTP de la API (200, 201, 202, 204, 400, 404, 409, 500).
 *
 * Se invoca cada endpoint a traves de MockMvc (capa HTTP real: serializacion JSON,
 * validacion Bean Validation y ApiExceptionHandler) con los repositorios y el
 * publicador MQTT simulados. No requiere base de datos, broker ni Docker.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HTTP | Codigos de estado de la API")
class CodigosHttpApiTest {

    @Mock private ReservaRepository reservaRepository;
    @Mock private MqttPublisherService mqttPublisherService;
    @Mock private EstablecimientoRepository establecimientoRepository;
    @Mock private PersonalRepository personalRepository;

    private MockMvc mvc;

    private static final String RESERVA_JSON = """
            {"idPersonal":1,"idEstablecimiento":1,"emailCliente":"cliente@correo.com",
             "telefonoCliente":"099123456","fecha":"2030-10-15","hora":"10:00"}""";

    private static final String ESTABLECIMIENTO_JSON = """
            {"nombreComercial":"TaPronto Central","direccion":"18 de Julio 1234","telefono":"29001234",
             "correoElectronico":"contacto@tapronto.com","horarioApertura":"08:00","horarioCierre":"20:00"}""";

    private static final String PERSONAL_JSON = """
            {"idEstablecimiento":1,"nombre":"Dra. Perez","especialidad":"Pediatria",
             "costoConsulta":1500.50,"duracionEstandarMinutos":30}""";

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

        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> {
            Reserva r = inv.getArgument(0);
            if (r.getId() == null) r.setId(1);
            return r;
        });
        when(establecimientoRepository.save(any(Establecimiento.class))).thenAnswer(inv -> {
            Establecimiento e = inv.getArgument(0);
            if (e.getId() == null) e.setId(1L);
            return e;
        });
        when(personalRepository.save(any(Personal.class))).thenAnswer(inv -> {
            Personal p = inv.getArgument(0);
            if (p.getId() == null) p.setId(1L);
            return p;
        });
    }

    // ---------------- helpers ----------------

    private Reserva reserva(int id, String estado) {
        Reserva r = new Reserva();
        r.setId(id);
        r.setIdPersonal(1);
        r.setIdEstablecimiento(1);
        r.setEmailSolicitante("cliente@correo.com");
        r.setTelefonoSolicitante("099123456");
        r.setFechaTurno(LocalDate.of(2030, 10, 15));
        r.setHoraTurno(LocalTime.of(10, 0));
        r.setDuracionMinutos(30);
        r.setEstado(estado);
        return r;
    }

    private Establecimiento establecimiento(long id) {
        Establecimiento e = new Establecimiento();
        e.setId(id);
        e.setNombreComercial("TaPronto Central");
        e.setDireccion("18 de Julio 1234");
        e.setTelefono("29001234");
        e.setCorreoElectronico("contacto@tapronto.com");
        e.setHorarioApertura(LocalTime.of(8, 0));
        e.setHorarioCierre(LocalTime.of(20, 0));
        return e;
    }

    private Personal personal(long id, EstadoPersonal estado) {
        return new Personal(id, 1L, "Dra. Perez", "Pediatria", new BigDecimal("1500.50"), 30, estado);
    }

    // =====================================================================
    // RESERVAS
    // =====================================================================

    @Test
    @DisplayName("CP-HTTP-01 | POST /reservas valido -> 201 Created")
    void postReservaValido201() throws Exception {
        mvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON).content(RESERVA_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SOLICITADO"));
        verify(mqttPublisherService, times(1)).publicarReserva(any());
    }

    @Test
    @DisplayName("CP-HTTP-02 | POST /reservas sin email -> 400 Bad Request y sin evento MQTT")
    void postReservaSinEmail400() throws Exception {
        String json = RESERVA_JSON.replace("\"emailCliente\":\"cliente@correo.com\",", "");
        mvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verify(mqttPublisherService, never()).publicarReserva(any());
        verify(reservaRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP-HTTP-03 | POST /reservas con email mal formado -> 400 Bad Request")
    void postReservaEmailInvalido400() throws Exception {
        String json = RESERVA_JSON.replace("cliente@correo.com", "no-es-un-email");
        mvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CP-HTTP-04 | POST /reservas con body vacio {} -> 400 Bad Request")
    void postReservaVacio400() throws Exception {
        mvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CP-HTTP-05 | GET /reservas -> 200 OK con lista")
    void getReservas200() throws Exception {
        when(reservaRepository.findAll()).thenReturn(List.of(reserva(1, "SOLICITADO")));
        mvc.perform(get("/reservas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("CP-HTTP-06 | GET /reservas?idPersonal=1 -> 200 OK filtrado")
    void getReservasPorPersonal200() throws Exception {
        when(reservaRepository.findByIdPersonal(1)).thenReturn(List.of(reserva(1, "SOLICITADO")));
        mvc.perform(get("/reservas").param("idPersonal", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idPersonal").value(1));
    }

    @Test
    @DisplayName("CP-HTTP-07 | GET /reservas/{id} existente -> 200 OK")
    void getReservaPorId200() throws Exception {
        when(reservaRepository.findById(5)).thenReturn(Optional.of(reserva(5, "CONFIRMADO")));
        mvc.perform(get("/reservas/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @DisplayName("CP-HTTP-08 | GET /reservas/{id} inexistente -> 404 Not Found")
    void getReservaInexistente404() throws Exception {
        when(reservaRepository.findById(anyInt())).thenReturn(Optional.empty());
        mvc.perform(get("/reservas/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("CP-HTTP-09 | PUT /reservas/{id} valido -> 202 Accepted y evento MQTT ACTUALIZAR")
    void putReserva202() throws Exception {
        when(reservaRepository.findById(5)).thenReturn(Optional.of(reserva(5, "CONFIRMADO")));
        mvc.perform(put("/reservas/5").contentType(MediaType.APPLICATION_JSON).content(RESERVA_JSON))
                .andExpect(status().isAccepted());
        verify(mqttPublisherService, times(1)).publicarReserva(any());
    }

    @Test
    @DisplayName("CP-HTTP-10 | PUT /reservas/{id} inexistente -> 404 Not Found")
    void putReservaInexistente404() throws Exception {
        when(reservaRepository.findById(anyInt())).thenReturn(Optional.empty());
        mvc.perform(put("/reservas/999").contentType(MediaType.APPLICATION_JSON).content(RESERVA_JSON))
                .andExpect(status().isNotFound());
        verify(mqttPublisherService, never()).publicarReserva(any());
    }

    @Test
    @DisplayName("CP-HTTP-11 | PUT /reservas/{id} con datos invalidos -> 400 Bad Request")
    void putReservaInvalida400() throws Exception {
        mvc.perform(put("/reservas/5").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CP-HTTP-12 | PUT /reservas/{id} de reserva Atendida -> 400 Bad Request")
    void putReservaAtendida400() throws Exception {
        when(reservaRepository.findById(5)).thenReturn(Optional.of(reserva(5, "Atendido")));
        mvc.perform(put("/reservas/5").contentType(MediaType.APPLICATION_JSON).content(RESERVA_JSON))
                .andExpect(status().isBadRequest());
        verify(mqttPublisherService, never()).publicarReserva(any());
    }

    @Test
    @DisplayName("CP-HTTP-13 | DELETE /reservas/{id} existente (cancelar turno) -> 204 No Content")
    void deleteReserva204() throws Exception {
        when(reservaRepository.findById(5)).thenReturn(Optional.of(reserva(5, "SOLICITADO")));
        mvc.perform(delete("/reservas/5"))
                .andExpect(status().isNoContent());
        verify(reservaRepository, times(1)).delete(any(Reserva.class));
    }

    @Test
    @DisplayName("CP-HTTP-14 | DELETE /reservas/{id} inexistente -> 404 Not Found")
    void deleteReservaInexistente404() throws Exception {
        when(reservaRepository.findById(anyInt())).thenReturn(Optional.empty());
        mvc.perform(delete("/reservas/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CP-HTTP-15 | DELETE /reservas/{id} de reserva Facturada -> 400 Bad Request")
    void deleteReservaFacturada400() throws Exception {
        when(reservaRepository.findById(5)).thenReturn(Optional.of(reserva(5, "Facturado")));
        mvc.perform(delete("/reservas/5"))
                .andExpect(status().isBadRequest());
        verify(reservaRepository, never()).delete(any(Reserva.class));
    }

    @Test
    @DisplayName("CP-HTTP-16 | Fallo inesperado del repositorio -> 500 Internal Server Error")
    void errorInterno500() throws Exception {
        when(reservaRepository.findAll()).thenThrow(new RuntimeException("BD caida"));
        mvc.perform(get("/reservas"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }

    // =====================================================================
    // ESTABLECIMIENTOS
    // =====================================================================

    @Test
    @DisplayName("CP-HTTP-17 | POST /establecimientos valido -> 201 Created")
    void postEstablecimiento201() throws Exception {
        mvc.perform(post("/establecimientos").contentType(MediaType.APPLICATION_JSON).content(ESTABLECIMIENTO_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("CP-HTTP-18 | POST /establecimientos con campos obligatorios vacios -> 400 Bad Request")
    void postEstablecimientoInvalido400() throws Exception {
        mvc.perform(post("/establecimientos").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(establecimientoRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP-HTTP-19 | POST /establecimientos con cierre anterior a apertura -> 400 Bad Request")
    void postEstablecimientoHorarioInvertido400() throws Exception {
        String json = ESTABLECIMIENTO_JSON.replace("\"horarioCierre\":\"20:00\"", "\"horarioCierre\":\"07:00\"");
        mvc.perform(post("/establecimientos").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CP-HTTP-20 | GET /establecimientos -> 200 OK")
    void getEstablecimientos200() throws Exception {
        when(establecimientoRepository.findAll()).thenReturn(List.of(establecimiento(1)));
        mvc.perform(get("/establecimientos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("CP-HTTP-21 | GET /establecimientos/{id} existente -> 200 OK")
    void getEstablecimiento200() throws Exception {
        when(establecimientoRepository.findById(1L)).thenReturn(Optional.of(establecimiento(1)));
        mvc.perform(get("/establecimientos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreComercial").value("TaPronto Central"));
    }

    @Test
    @DisplayName("CP-HTTP-22 | GET /establecimientos/{id} inexistente -> 404 Not Found")
    void getEstablecimientoInexistente404() throws Exception {
        when(establecimientoRepository.findById(anyLong())).thenReturn(Optional.empty());
        mvc.perform(get("/establecimientos/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("CP-HTTP-23 | PUT /establecimientos/{id} valido -> 200 OK")
    void putEstablecimiento200() throws Exception {
        when(establecimientoRepository.findById(1L)).thenReturn(Optional.of(establecimiento(1)));
        mvc.perform(put("/establecimientos/1").contentType(MediaType.APPLICATION_JSON).content(ESTABLECIMIENTO_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CP-HTTP-24 | PUT /establecimientos/{id} inexistente -> 404 Not Found")
    void putEstablecimientoInexistente404() throws Exception {
        when(establecimientoRepository.findById(anyLong())).thenReturn(Optional.empty());
        mvc.perform(put("/establecimientos/999").contentType(MediaType.APPLICATION_JSON).content(ESTABLECIMIENTO_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CP-HTTP-25 | DELETE /establecimientos/{id} existente -> 204 No Content")
    void deleteEstablecimiento204() throws Exception {
        when(establecimientoRepository.existsById(1L)).thenReturn(true);
        mvc.perform(delete("/establecimientos/1"))
                .andExpect(status().isNoContent());
        verify(establecimientoRepository).deleteById(1L);
    }

    @Test
    @DisplayName("CP-HTTP-26 | DELETE /establecimientos/{id} inexistente -> 404 Not Found")
    void deleteEstablecimientoInexistente404() throws Exception {
        when(establecimientoRepository.existsById(anyLong())).thenReturn(false);
        mvc.perform(delete("/establecimientos/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CP-HTTP-27 | DELETE /establecimientos/{id} con datos asociados -> 409 Conflict")
    void deleteEstablecimientoConDependencias409() throws Exception {
        when(establecimientoRepository.existsById(1L)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("FK")).when(establecimientoRepository).deleteById(1L);
        mvc.perform(delete("/establecimientos/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // =====================================================================
    // PERSONAL
    // =====================================================================

    @Test
    @DisplayName("CP-HTTP-28 | POST /personal valido -> 201 Created")
    void postPersonal201() throws Exception {
        when(establecimientoRepository.existsById(1L)).thenReturn(true);
        mvc.perform(post("/personal").contentType(MediaType.APPLICATION_JSON).content(PERSONAL_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("CP-HTTP-29 | POST /personal con establecimiento inexistente -> 400 Bad Request")
    void postPersonalEstablecimientoInexistente400() throws Exception {
        when(establecimientoRepository.existsById(anyLong())).thenReturn(false);
        mvc.perform(post("/personal").contentType(MediaType.APPLICATION_JSON).content(PERSONAL_JSON))
                .andExpect(status().isBadRequest());
        verify(personalRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP-HTTP-30 | POST /personal con costo 0 y sin nombre -> 400 Bad Request")
    void postPersonalInvalido400() throws Exception {
        String json = """
                {"idEstablecimiento":1,"especialidad":"Pediatria","costoConsulta":0,"duracionEstandarMinutos":30}""";
        mvc.perform(post("/personal").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CP-HTTP-31 | GET /personal -> 200 OK")
    void getPersonal200() throws Exception {
        when(personalRepository.findAll()).thenReturn(List.of(personal(1, EstadoPersonal.ACTIVO)));
        mvc.perform(get("/personal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("CP-HTTP-32 | GET /personal/{id} existente -> 200 OK")
    void getPersonalPorId200() throws Exception {
        when(personalRepository.findById(1L)).thenReturn(Optional.of(personal(1, EstadoPersonal.ACTIVO)));
        mvc.perform(get("/personal/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Dra. Perez"));
    }

    @Test
    @DisplayName("CP-HTTP-33 | GET /personal/{id} inexistente (profesional inexistente) -> 404 Not Found")
    void getPersonalInexistente404() throws Exception {
        when(personalRepository.findById(anyLong())).thenReturn(Optional.empty());
        mvc.perform(get("/personal/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CP-HTTP-34 | PUT /personal/{id} valido -> 200 OK")
    void putPersonal200() throws Exception {
        when(personalRepository.findById(1L)).thenReturn(Optional.of(personal(1, EstadoPersonal.ACTIVO)));
        when(establecimientoRepository.existsById(1L)).thenReturn(true);
        mvc.perform(put("/personal/1").contentType(MediaType.APPLICATION_JSON).content(PERSONAL_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CP-HTTP-35 | PUT /personal/{id} inexistente -> 404 Not Found")
    void putPersonalInexistente404() throws Exception {
        when(personalRepository.findById(anyLong())).thenReturn(Optional.empty());
        mvc.perform(put("/personal/999").contentType(MediaType.APPLICATION_JSON).content(PERSONAL_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CP-HTTP-36 | DELETE /personal/{id} existente -> 204 No Content")
    void deletePersonal204() throws Exception {
        when(personalRepository.existsById(1L)).thenReturn(true);
        mvc.perform(delete("/personal/1"))
                .andExpect(status().isNoContent());
        verify(personalRepository).deleteById(1L);
    }

    @Test
    @DisplayName("CP-HTTP-37 | DELETE /personal/{id} inexistente -> 404 Not Found")
    void deletePersonalInexistente404() throws Exception {
        when(personalRepository.existsById(anyLong())).thenReturn(false);
        mvc.perform(delete("/personal/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CP-HTTP-38 | DELETE /personal/{id} con turnos asociados -> 409 Conflict")
    void deletePersonalConTurnos409() throws Exception {
        when(personalRepository.existsById(1L)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("FK")).when(personalRepository).deleteById(1L);
        mvc.perform(delete("/personal/1"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("CP-HTTP-39 | GET /personal/{id}/agenda?fecha= valido -> 200 OK")
    void getAgenda200() throws Exception {
        when(personalRepository.findById(1L)).thenReturn(Optional.of(personal(1, EstadoPersonal.ACTIVO)));
        when(reservaRepository.findByIdPersonalAndFechaTurnoOrderByHoraTurnoAsc(anyInt(), any()))
                .thenReturn(List.of(reserva(1, "CONFIRMADO")));
        mvc.perform(get("/personal/1/agenda").param("fecha", "2030-10-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnos.length()").value(1));
    }

    @Test
    @DisplayName("CP-HTTP-40 | GET /personal/{id}/agenda de profesional inexistente -> 404 Not Found")
    void getAgendaPersonalInexistente404() throws Exception {
        when(personalRepository.findById(anyLong())).thenReturn(Optional.empty());
        mvc.perform(get("/personal/999/agenda").param("fecha", "2030-10-15"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CP-HTTP-41 | GET /personal/{id}/disponibilidad (confirmar disponibilidad) -> 200 OK con horarios")
    void getDisponibilidad200() throws Exception {
        when(personalRepository.findById(1L)).thenReturn(Optional.of(personal(1, EstadoPersonal.ACTIVO)));
        when(establecimientoRepository.findById(1L)).thenReturn(Optional.of(establecimiento(1)));
        when(reservaRepository.findByIdPersonalAndFechaTurnoOrderByHoraTurnoAsc(anyInt(), any()))
                .thenReturn(List.of());
        mvc.perform(get("/personal/1/disponibilidad").param("fecha", "2030-10-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horariosDisponibles.length()").value(24));
    }

    @Test
    @DisplayName("CP-HTTP-42 | GET /personal/{id}/disponibilidad de profesional inexistente -> 404 Not Found")
    void getDisponibilidadPersonalInexistente404() throws Exception {
        when(personalRepository.findById(anyLong())).thenReturn(Optional.empty());
        mvc.perform(get("/personal/999/disponibilidad").param("fecha", "2030-10-15"))
                .andExpect(status().isNotFound());
    }
}
