package com.laboratorio.turnos.subscriber.service;

import com.laboratorio.turnos.subscriber.model.TurnoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalTime;

/**
 * Validador de las reglas de negocio del dominio para las reservas de turnos:
 * 1. El profesional debe existir y estar en estado 'ACTIVO'.
 * 2. El horario del turno (duración 30 min) debe estar dentro de la agenda del establecimiento.
 * 3. No puede existir otro turno para el mismo profesional en el mismo horario.
 */
public class TurnoValidator {

    private static final Logger logger = LoggerFactory.getLogger(TurnoValidator.class);
    private static final int DURACION_TURNO_MINUTOS = 30;

    public static class ValidationResult {
        private final boolean valid;
        private final String reason;
        private final String nombreProfesional;
        private final String nombreEstablecimiento;

        private ValidationResult(boolean valid, String reason, String nombreProfesional, String nombreEstablecimiento) {
            this.valid = valid;
            this.reason = reason;
            this.nombreProfesional = nombreProfesional;
            this.nombreEstablecimiento = nombreEstablecimiento;
        }

        public static ValidationResult ok(String nombreProfesional, String nombreEstablecimiento) {
            return new ValidationResult(true, "Reserva válida", nombreProfesional, nombreEstablecimiento);
        }

        public static ValidationResult reject(String reason) {
            return new ValidationResult(false, reason, null, null);
        }

        public boolean isValid() {
            return valid;
        }

        public String getReason() {
            return reason;
        }

        public String getNombreProfesional() {
            return nombreProfesional;
        }

        public String getNombreEstablecimiento() {
            return nombreEstablecimiento;
        }
    }

    /**
     * Valida si la solicitud de turno cumple todas las reglas de negocio.
     */
    public ValidationResult validate(TurnoDTO turno, Connection conn) throws SQLException {
        if (turno == null) {
            return ValidationResult.reject("El contenido del turno es nulo.");
        }
        if (turno.getIdPersonal() == null) {
            return ValidationResult.reject("El idPersonal es obligatorio.");
        }
        if (turno.getFecha() == null || turno.getHora() == null) {
            return ValidationResult.reject("La fecha y la hora del turno son obligatorias.");
        }
        if (turno.getEmailCliente() == null || turno.getEmailCliente().isBlank()) {
            return ValidationResult.reject("El email del cliente es obligatorio.");
        }

        // 1. Obtener datos del profesional y de su establecimiento
        String queryPersonal = """
            SELECT p.id, p.nombre, p.estado, e.nombre_comercial, e.horario_apertura, e.horario_cierre
            FROM personal p
            INNER JOIN establecimientos e ON p.id_establecimiento = e.id
            WHERE p.id = ?
        """;

        String nombreProfesional;
        String nombreEstablecimiento;
        LocalTime apertura;
        LocalTime cierre;
        String estadoPersonal;

        try (PreparedStatement ps = conn.prepareStatement(queryPersonal)) {
            ps.setInt(1, turno.getIdPersonal());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return ValidationResult.reject(String.format("El profesional con ID %d no existe en el sistema.", turno.getIdPersonal()));
                }

                nombreProfesional = rs.getString("nombre");
                nombreEstablecimiento = rs.getString("nombre_comercial");
                apertura = rs.getTime("horario_apertura").toLocalTime();
                cierre = rs.getTime("horario_cierre").toLocalTime();
                estadoPersonal = rs.getString("estado");
            }
        }

        // Regla: No podrán registrarse profesionales/empleados inactivos
        if (!"ACTIVO".equalsIgnoreCase(estadoPersonal)) {
            return ValidationResult.reject(String.format(
                    "REGLA VIOLADA: El profesional '%s' (ID %d) está en estado '%s'. No se pueden asignar turnos a personal inactivo.",
                    nombreProfesional, turno.getIdPersonal(), estadoPersonal
            ));
        }

        // Regla: No podrán reservarse horarios fuera de la agenda (asumiendo horario del establecimiento)
        LocalTime horaInicio = turno.getHora();
        LocalTime horaFin = horaInicio.plusMinutes(DURACION_TURNO_MINUTOS);

        if (horaInicio.isBefore(apertura) || horaFin.isAfter(cierre)) {
            return ValidationResult.reject(String.format(
                    "REGLA VIOLADA: Horario %s - %s fuera de la agenda de '%s' (Apertura: %s, Cierre: %s).",
                    horaInicio, horaFin, nombreEstablecimiento, apertura, cierre
            ));
        }

        // Regla: No podrán existir dos turnos para el mismo profesional en el mismo horario
        String queryConflicto = """
            SELECT COUNT(*) FROM reservas_turnos
            WHERE id_personal = ? AND fecha_turno = ? AND hora_turno = ? AND estado = 'CONFIRMADO'
        """;

        try (PreparedStatement ps = conn.prepareStatement(queryConflicto)) {
            ps.setInt(1, turno.getIdPersonal());
            ps.setDate(2, Date.valueOf(turno.getFecha()));
            ps.setTime(3, Time.valueOf(turno.getHora()));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return ValidationResult.reject(String.format(
                            "REGLA VIOLADA: El profesional '%s' ya tiene un turno reservado para el día %s a las %s.",
                            nombreProfesional, turno.getFecha(), turno.getHora()
                    ));
                }
            }
        }

        return ValidationResult.ok(nombreProfesional, nombreEstablecimiento);
    }
}
