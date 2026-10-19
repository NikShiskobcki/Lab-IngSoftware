package com.laboratorio.turnos.api.validacion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.laboratorio.turnos.api.dto.CrearReservaDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * US-51 - Validaciones de entrada de Reserva (CrearReservaDTO).
 * Solo usa Bean Validation y Jackson: no levanta Spring ni toca la base de datos.
 */
@DisplayName("US-51 | Validaciones de entrada: Reserva")
class CrearReservaDTOValidacionTest {

    private static ValidatorFactory factory;
    private static Validator validator;
    private static ObjectMapper mapper;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @AfterAll
    static void cerrar() {
        factory.close();
    }

    private CrearReservaDTO dtoValido() {
        CrearReservaDTO dto = new CrearReservaDTO();
        dto.setIdPersonal(1);
        dto.setIdEstablecimiento(1);
        dto.setEmailCliente("cliente@correo.com");
        dto.setTelefonoCliente("099123456");
        dto.setFecha(LocalDate.now().plusDays(1));
        dto.setHora(LocalTime.of(10, 0));
        return dto;
    }

    private boolean fallaEn(CrearReservaDTO dto, String propiedad) {
        Set<ConstraintViolation<CrearReservaDTO>> v = validator.validate(dto);
        return v.stream().anyMatch(x -> x.getPropertyPath().toString().equals(propiedad));
    }

    @Test
    @DisplayName("Un DTO completo y correcto no genera violaciones")
    void dtoValido_sinViolaciones() {
        assertTrue(validator.validate(dtoValido()).isEmpty());
    }

    // ---------- campos obligatorios ----------

    @Test
    @DisplayName("idPersonal es obligatorio")
    void idPersonalNulo() {
        CrearReservaDTO dto = dtoValido();
        dto.setIdPersonal(null);
        assertTrue(fallaEn(dto, "idPersonal"));
    }

    @Test
    @DisplayName("idEstablecimiento es obligatorio")
    void idEstablecimientoNulo() {
        CrearReservaDTO dto = dtoValido();
        dto.setIdEstablecimiento(null);
        assertTrue(fallaEn(dto, "idEstablecimiento"));
    }

    @Test
    @DisplayName("La fecha es obligatoria")
    void fechaNula() {
        CrearReservaDTO dto = dtoValido();
        dto.setFecha(null);
        assertTrue(fallaEn(dto, "fecha"));
    }

    @Test
    @DisplayName("La hora es obligatoria")
    void horaNula() {
        CrearReservaDTO dto = dtoValido();
        dto.setHora(null);
        assertTrue(fallaEn(dto, "hora"));
    }

    @ParameterizedTest(name = "telefono \"{0}\" es rechazado")
    @ValueSource(strings = {"", "   "})
    void telefonoVacio(String telefono) {
        CrearReservaDTO dto = dtoValido();
        dto.setTelefonoCliente(telefono);
        assertTrue(fallaEn(dto, "telefonoCliente"));
    }

    @Test
    @DisplayName("El telefono nulo es rechazado")
    void telefonoNulo() {
        CrearReservaDTO dto = dtoValido();
        dto.setTelefonoCliente(null);
        assertTrue(fallaEn(dto, "telefonoCliente"));
    }

    @Test
    @DisplayName("Un DTO vacio informa todos los campos obligatorios faltantes")
    void dtoVacio_reportaTodosLosCampos() {
        Set<ConstraintViolation<CrearReservaDTO>> v = validator.validate(new CrearReservaDTO());
        Set<String> campos = new java.util.HashSet<>();
        v.forEach(x -> campos.add(x.getPropertyPath().toString()));
        assertTrue(campos.containsAll(Set.of(
                "idPersonal", "idEstablecimiento", "emailCliente",
                "telefonoCliente", "fecha", "hora")));
    }

    // ---------- email ----------

    @Test
    @DisplayName("El email nulo es rechazado")
    void emailNulo() {
        CrearReservaDTO dto = dtoValido();
        dto.setEmailCliente(null);
        assertTrue(fallaEn(dto, "emailCliente"));
    }

    @ParameterizedTest(name = "email \"{0}\" es rechazado")
    @ValueSource(strings = {"", "   ", "sin-arroba", "@dominio.com", "usuario@", "dos@@dominio.com"})
    void emailInvalido(String email) {
        CrearReservaDTO dto = dtoValido();
        dto.setEmailCliente(email);
        assertTrue(fallaEn(dto, "emailCliente"));
    }

    @ParameterizedTest(name = "email \"{0}\" es aceptado")
    @ValueSource(strings = {"cliente@correo.com", "nombre.apellido@empresa.com.uy", "a+tag@dominio.org"})
    void emailValido(String email) {
        CrearReservaDTO dto = dtoValido();
        dto.setEmailCliente(email);
        assertFalse(fallaEn(dto, "emailCliente"));
    }

    // ---------- formato de fecha y horario (deserializacion JSON) ----------

    @Test
    @DisplayName("JSON con fecha yyyy-MM-dd y hora HH:mm se deserializa correctamente")
    void jsonFormatoCorrecto() throws Exception {
        String json = """
                {"idPersonal":1,"idEstablecimiento":1,"emailCliente":"a@b.com",
                 "telefonoCliente":"099","fecha":"2026-10-15","hora":"10:30"}""";
        CrearReservaDTO dto = mapper.readValue(json, CrearReservaDTO.class);
        assertEquals(LocalDate.of(2026, 10, 15), dto.getFecha());
        assertEquals(LocalTime.of(10, 30), dto.getHora());
    }

    @ParameterizedTest(name = "hora \"{0}\" no es un horario valido")
    @ValueSource(strings = {"25:00", "10h30", "diez", "10:75"})
    void horaConFormatoInvalido(String hora) {
        String json = "{\"hora\":\"" + hora + "\"}";
        assertThrows(JsonProcessingException.class,
                () -> mapper.readValue(json, CrearReservaDTO.class));
    }

    @ParameterizedTest(name = "fecha \"{0}\" no respeta yyyy-MM-dd")
    @ValueSource(strings = {"15/10/2026", "texto", "2026-13-01"})
    void fechaConFormatoInvalido(String fecha) {
        String json = "{\"fecha\":\"" + fecha + "\"}";
        assertThrows(JsonProcessingException.class,
                () -> mapper.readValue(json, CrearReservaDTO.class));
    }
}
