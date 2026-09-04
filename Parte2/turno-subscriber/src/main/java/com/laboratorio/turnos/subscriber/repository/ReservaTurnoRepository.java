package com.laboratorio.turnos.subscriber.repository;

import com.laboratorio.turnos.subscriber.model.TurnoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Repositorio de persistencia de reservas de turnos en MariaDB.
 */
public class ReservaTurnoRepository {

    private static final Logger logger = LoggerFactory.getLogger(ReservaTurnoRepository.class);

    /**
     * Persiste una nueva reserva de turno en la base de datos.
     *
     * @param turno DTO del turno solicitado.
     * @param fechaRegistro Timestamp de cuando se emitió la solicitud.
     * @param conn Conexión SQL activa.
     * @return El ID generado en la base de datos.
     */
    public int saveReserva(TurnoDTO turno, LocalDateTime fechaRegistro, Connection conn) throws SQLException {
        String sql = """
            INSERT INTO reservas_turnos 
            (id_personal, email_solicitante, telefono_solicitante, fecha_turno, hora_turno, duracion_minutos, fecha_registro, estado)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, turno.getIdPersonal());
            ps.setString(2, turno.getEmailCliente());
            ps.setString(3, turno.getTelefonoCliente());
            ps.setDate(4, Date.valueOf(turno.getFecha()));
            ps.setTime(5, Time.valueOf(turno.getHora()));
            ps.setInt(6, 30); // Duración fija de 30 minutos según regla de negocio
            ps.setTimestamp(7, Timestamp.valueOf(fechaRegistro != null ? fechaRegistro : LocalDateTime.now()));
            ps.setString(8, "CONFIRMADO");

            int affectedRows = ps.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Error al insertar reserva, ninguna fila afectada.");
            }

            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int idGenerado = generatedKeys.getInt(1);
                    logger.info("Reserva persistida exitosamente con ID_BD={}", idGenerado);
                    return idGenerado;
                } else {
                    throw new SQLException("Error al obtener el ID autogenerado de la reserva.");
                }
            }
        }
    }
}
