package com.laboratorio.turnos.api.validacion;

import com.laboratorio.turnos.api.personal.EstadoPersonal;
import com.laboratorio.turnos.api.personal.dto.PersonalInput;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** US-51 - Validaciones de entrada de Personal (sin Spring ni base de datos). */
@DisplayName("US-51 | Validaciones de entrada: Personal")
class PersonalInputValidacionTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cerrar() {
        factory.close();
    }

    private PersonalInput inputValido() {
        PersonalInput in = new PersonalInput();
        in.setIdEstablecimiento(1L);
        in.setNombre("Dra. Ana Perez");
        in.setEspecialidad("Pediatria");
        in.setCostoConsulta(new BigDecimal("1500.00"));
        in.setDuracionEstandarMinutos(30);
        return in;
    }

    private boolean fallaEn(PersonalInput in, String propiedad) {
        Set<ConstraintViolation<PersonalInput>> v = validator.validate(in);
        return v.stream().anyMatch(x -> x.getPropertyPath().toString().equals(propiedad));
    }

    @Test
    @DisplayName("Un personal completo y correcto no genera violaciones")
    void inputValido_sinViolaciones() {
        assertTrue(validator.validate(inputValido()).isEmpty());
    }

    @Test
    @DisplayName("El estado es opcional (por defecto lo resuelve el controlador)")
    void estadoOpcional() {
        PersonalInput in = inputValido();
        in.setEstado(null);
        assertTrue(validator.validate(in).isEmpty());
        in.setEstado(EstadoPersonal.INACTIVO);
        assertTrue(validator.validate(in).isEmpty());
    }

    @Test
    @DisplayName("idEstablecimiento es obligatorio")
    void idEstablecimientoObligatorio() {
        PersonalInput in = inputValido();
        in.setIdEstablecimiento(null);
        assertTrue(fallaEn(in, "idEstablecimiento"));
    }

    @ParameterizedTest(name = "nombre \"{0}\" es rechazado")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void nombreObligatorio(String valor) {
        PersonalInput in = inputValido();
        in.setNombre(valor);
        assertTrue(fallaEn(in, "nombre"));
    }

    @ParameterizedTest(name = "especialidad \"{0}\" es rechazada")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void especialidadObligatoria(String valor) {
        PersonalInput in = inputValido();
        in.setEspecialidad(valor);
        assertTrue(fallaEn(in, "especialidad"));
    }

    @Test
    @DisplayName("costoConsulta es obligatorio")
    void costoObligatorio() {
        PersonalInput in = inputValido();
        in.setCostoConsulta(null);
        assertTrue(fallaEn(in, "costoConsulta"));
    }

    @ParameterizedTest(name = "costoConsulta {0} es rechazado (debe ser > 0)")
    @ValueSource(strings = {"0", "0.00", "-1", "-100.50"})
    void costoDebeSerMayorQueCero(String costo) {
        PersonalInput in = inputValido();
        in.setCostoConsulta(new BigDecimal(costo));
        assertTrue(fallaEn(in, "costoConsulta"));
    }

    @Test
    @DisplayName("costoConsulta con un valor minimo positivo es aceptado")
    void costoMinimoPositivo() {
        PersonalInput in = inputValido();
        in.setCostoConsulta(new BigDecimal("0.01"));
        assertFalse(fallaEn(in, "costoConsulta"));
    }

    @Test
    @DisplayName("duracionEstandarMinutos es obligatoria")
    void duracionObligatoria() {
        PersonalInput in = inputValido();
        in.setDuracionEstandarMinutos(null);
        assertTrue(fallaEn(in, "duracionEstandarMinutos"));
    }

    @ParameterizedTest(name = "duracionEstandarMinutos {0} es rechazada (debe ser > 0)")
    @ValueSource(ints = {0, -1, -30})
    void duracionDebeSerPositiva(int minutos) {
        PersonalInput in = inputValido();
        in.setDuracionEstandarMinutos(minutos);
        assertTrue(fallaEn(in, "duracionEstandarMinutos"));
    }

    @Test
    @DisplayName("Un input vacio informa todos los campos obligatorios")
    void inputVacio_reportaTodosLosCampos() {
        Set<ConstraintViolation<PersonalInput>> v = validator.validate(new PersonalInput());
        Set<String> campos = new java.util.HashSet<>();
        v.forEach(x -> campos.add(x.getPropertyPath().toString()));
        assertTrue(campos.containsAll(Set.of(
                "idEstablecimiento", "nombre", "especialidad",
                "costoConsulta", "duracionEstandarMinutos")));
    }
}
