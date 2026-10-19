package com.laboratorio.turnos.api.validacion;

import com.laboratorio.turnos.api.establecimiento.dto.EstablecimientoInput;
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

import java.time.LocalTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** US-51 - Validaciones de entrada de Establecimiento (sin Spring ni base de datos). */
@DisplayName("US-51 | Validaciones de entrada: Establecimiento")
class EstablecimientoInputValidacionTest {

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

    private EstablecimientoInput inputValido() {
        EstablecimientoInput in = new EstablecimientoInput();
        in.setNombreComercial("Clinica Central");
        in.setDireccion("Av. Principal 123");
        in.setTelefono("42221234");
        in.setCorreoElectronico("contacto@clinica.com");
        in.setHorarioApertura(LocalTime.of(9, 0));
        in.setHorarioCierre(LocalTime.of(18, 0));
        return in;
    }

    private boolean fallaEn(EstablecimientoInput in, String propiedad) {
        Set<ConstraintViolation<EstablecimientoInput>> v = validator.validate(in);
        return v.stream().anyMatch(x -> x.getPropertyPath().toString().equals(propiedad));
    }

    @Test
    @DisplayName("Un establecimiento completo y correcto no genera violaciones")
    void inputValido_sinViolaciones() {
        assertTrue(validator.validate(inputValido()).isEmpty());
    }

    @ParameterizedTest(name = "nombreComercial \"{0}\" es rechazado")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void nombreObligatorio(String valor) {
        EstablecimientoInput in = inputValido();
        in.setNombreComercial(valor);
        assertTrue(fallaEn(in, "nombreComercial"));
    }

    @ParameterizedTest(name = "direccion \"{0}\" es rechazada")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void direccionObligatoria(String valor) {
        EstablecimientoInput in = inputValido();
        in.setDireccion(valor);
        assertTrue(fallaEn(in, "direccion"));
    }

    @ParameterizedTest(name = "telefono \"{0}\" es rechazado")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void telefonoObligatorio(String valor) {
        EstablecimientoInput in = inputValido();
        in.setTelefono(valor);
        assertTrue(fallaEn(in, "telefono"));
    }

    @ParameterizedTest(name = "correoElectronico \"{0}\" es rechazado")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "sin-arroba", "@dominio.com", "usuario@"})
    void correoInvalidoOVacio(String valor) {
        EstablecimientoInput in = inputValido();
        in.setCorreoElectronico(valor);
        assertTrue(fallaEn(in, "correoElectronico"));
    }

    @Test
    @DisplayName("horarioApertura es obligatorio")
    void aperturaObligatoria() {
        EstablecimientoInput in = inputValido();
        in.setHorarioApertura(null);
        assertTrue(fallaEn(in, "horarioApertura"));
    }

    @Test
    @DisplayName("horarioCierre es obligatorio")
    void cierreObligatorio() {
        EstablecimientoInput in = inputValido();
        in.setHorarioCierre(null);
        assertTrue(fallaEn(in, "horarioCierre"));
    }

    @Test
    @DisplayName("Un input vacio informa todos los campos obligatorios")
    void inputVacio_reportaTodosLosCampos() {
        Set<ConstraintViolation<EstablecimientoInput>> v = validator.validate(new EstablecimientoInput());
        Set<String> campos = new java.util.HashSet<>();
        v.forEach(x -> campos.add(x.getPropertyPath().toString()));
        assertTrue(campos.containsAll(Set.of(
                "nombreComercial", "direccion", "telefono",
                "correoElectronico", "horarioApertura", "horarioCierre")));
    }
}
