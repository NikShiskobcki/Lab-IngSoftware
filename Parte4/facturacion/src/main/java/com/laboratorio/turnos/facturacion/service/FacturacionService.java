package com.laboratorio.turnos.facturacion.service;

import com.laboratorio.turnos.facturacion.model.ResumenCiclo;
import com.laboratorio.turnos.facturacion.repository.ReservaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

// logica de facturacion: recorre los turnos atendidos y los agrega a la factura mensual del cliente
@Service
public class FacturacionService {

    private static final Logger logger = LoggerFactory.getLogger(FacturacionService.class);

    private final ReservaRepository reservas;
    private final TurnoFacturador turnoFacturador;

    public FacturacionService(ReservaRepository reservas, TurnoFacturador turnoFacturador) {
        this.reservas = reservas;
        this.turnoFacturador = turnoFacturador;
    }

    // una pasada completa sobre los turnos atendidos
    public ResumenCiclo ejecutarCiclo() {
        LocalDateTime inicio = LocalDateTime.now();
        logger.info("iniciando ciclo de facturacion ({})", inicio);

        int encontrados = 0;
        int facturados = 0;
        int omitidos = 0;
        int errores = 0;

        try {
            List<Integer> ids = reservas.buscarIdsPorEstado(TurnoFacturador.ESTADO_ATENDIDO);
            encontrados = ids.size();

            for (int idTurno : ids) {
                try {
                    if (turnoFacturador.facturar(idTurno)) {
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
        } catch (Exception e) {
            errores++;
            logger.error("error de base de datos durante el ciclo de facturacion: {}", e.getMessage());
        }

        ResumenCiclo resumen = new ResumenCiclo(encontrados, facturados, omitidos, errores);
        long ms = Duration.between(inicio, LocalDateTime.now()).toMillis();
        logger.info("ciclo de facturacion finalizado: inicio={}, turnos atendidos encontrados={}, facturados={}, omitidos={}, errores={}, duracion={}ms",
                inicio, resumen.encontrados(), resumen.facturados(), resumen.omitidos(), resumen.errores(), ms);
        return resumen;
    }
}
