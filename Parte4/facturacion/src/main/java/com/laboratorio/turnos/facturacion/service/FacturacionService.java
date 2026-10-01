package com.laboratorio.turnos.facturacion.service;

import com.laboratorio.turnos.facturacion.db.DatabaseManager;
import com.laboratorio.turnos.facturacion.model.ResumenCiclo;
import com.laboratorio.turnos.facturacion.model.TurnoAtendido;
import com.laboratorio.turnos.facturacion.repository.FacturacionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

// logica de facturacion: recorre los turnos atendidos y los agrega a la factura mensual del cliente
public class FacturacionService {

    private static final Logger logger = LoggerFactory.getLogger(FacturacionService.class);

    private final DatabaseManager databaseManager;
    private final FacturacionRepository repository;

    public FacturacionService(DatabaseManager databaseManager, FacturacionRepository repository) {
        this.databaseManager = databaseManager;
        this.repository = repository;
    }

    // us-40/us-41: una pasada completa sobre los turnos atendidos
    public ResumenCiclo ejecutarCiclo() {
        LocalDateTime inicio = LocalDateTime.now();
        logger.info("iniciando ciclo de facturacion ({})", inicio);

        int encontrados = 0;
        int facturados = 0;
        int omitidos = 0;
        int errores = 0;

        try (Connection conn = databaseManager.getConnection()) {
            List<Integer> ids = repository.buscarIdsTurnosAtendidos(conn);
            encontrados = ids.size();

            for (int idTurno : ids) {
                try {
                    if (facturarTurno(conn, idTurno)) {
                        facturados++;
                    } else {
                        omitidos++;
                    }
                } catch (Exception e) {
                    // un turno con problemas no debe frenar a los demas; queda atendido y se reintenta en el proximo ciclo
                    errores++;
                    logger.error("no se pudo facturar el turno id={}: {}", idTurno, e.getMessage());
                }
            }
        } catch (SQLException e) {
            errores++;
            logger.error("error de base de datos durante el ciclo de facturacion: {}", e.getMessage());
        }

        ResumenCiclo resumen = new ResumenCiclo(encontrados, facturados, omitidos, errores);
        long ms = java.time.Duration.between(inicio, LocalDateTime.now()).toMillis();
        logger.info("ciclo de facturacion finalizado: inicio={}, turnos atendidos encontrados={}, facturados={}, omitidos={}, errores={}, duracion={}ms",
                inicio, resumen.encontrados(), resumen.facturados(), resumen.omitidos(), resumen.errores(), ms);
        return resumen;
    }

    // us-41/us-42: item de factura + cambio de estado en una unica transaccion.
    // devuelve false si el turno ya no estaba atendido (no se factura).
    boolean facturarTurno(Connection conn, int idTurno) throws SQLException {
        conn.setAutoCommit(false);
        try {
            Optional<TurnoAtendido> opt = repository.bloquearTurnoAtendido(conn, idTurno);
            if (opt.isEmpty()) {
                conn.rollback();
                logger.info("turno id={} omitido: ya no esta en estado Atendido", idTurno);
                return false;
            }
            TurnoAtendido turno = opt.get();

            // el cliente se identifica por email (sin distinguir mayusculas ni espacios)
            String email = turno.emailCliente().trim().toLowerCase(Locale.ROOT);
            int idCliente = repository.obtenerOCrearCliente(conn, email, turno.telefonoCliente());

            // el mes de la factura es el del turno, no el de ejecucion del proceso
            int anio = turno.fechaTurno().getYear();
            int mes = turno.fechaTurno().getMonthValue();
            int idFactura = repository.obtenerOCrearFactura(conn, idCliente, anio, mes);

            String descripcion = String.format("Turno #%d - %s (%s) - %s %s",
                    turno.id(), turno.nombrePersonal(), turno.especialidad(),
                    turno.fechaTurno(), turno.horaTurno().toString().substring(0, 5));
            repository.insertarItem(conn, idFactura, turno.id(), descripcion, turno.costoConsulta());
            repository.recalcularTotal(conn, idFactura);

            // el estado cambia recien despues de agregar el item
            if (!repository.marcarFacturado(conn, turno.id())) {
                throw new SQLException("no se pudo marcar como Facturado el turno id=" + turno.id());
            }

            conn.commit();
            logger.info("turno id={} facturado: cliente={}, factura id={} ({}/{}), monto={}",
                    turno.id(), email, idFactura, String.format("%02d", mes), anio, turno.costoConsulta());
            return true;
        } catch (SQLException | RuntimeException e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
                // la conexion ya esta rota; se reporta el error original
            }
            throw e;
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
                // sin accion
            }
        }
    }
}
