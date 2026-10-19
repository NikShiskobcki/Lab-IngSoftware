package com.laboratorio.turnos.facturacion.service;

import com.laboratorio.turnos.facturacion.model.ResumenCiclo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class FacturacionService {

    private static final Logger logger =
            LoggerFactory.getLogger(FacturacionService.class);

    private final ColaFacturacion cola;
    private final TurnoFacturador turnoFacturador;

    public FacturacionService(
            ColaFacturacion cola,
            TurnoFacturador turnoFacturador) {

        this.cola = cola;
        this.turnoFacturador = turnoFacturador;
    }

    // Facturar los turnos recibidos por MQTT
    public ResumenCiclo ejecutarCiclo() {
        LocalDateTime inicio = LocalDateTime.now();
        List<Integer> ids = cola.retirarPendientes();

        int encontrados = ids.size();
        int facturados = 0;
        int omitidos = 0;
        int errores = 0;

        logger.info(
                "Iniciando facturacion de {} eventos pendientes",
                encontrados
        );

        for (Integer idTurno : ids) {
            try {
                if (turnoFacturador.facturar(idTurno)) {
                    facturados++;
                } else {
                    omitidos++;
                }
            } catch (Exception e) {
                errores++;
                cola.agregar(idTurno);

                logger.error(
                        "No se pudo facturar el turno {}",
                        idTurno,
                        e
                );
            }
        }

        ResumenCiclo resumen = new ResumenCiclo(
                encontrados,
                facturados,
                omitidos,
                errores
        );

        long ms = Duration.between(
                inicio,
                LocalDateTime.now()
        ).toMillis();

        logger.info(
                "Facturacion finalizada: pendientes={}, facturados={}, omitidos={}, errores={}, duracion={}ms",
                encontrados,
                facturados,
                omitidos,
                errores,
                ms
        );

        return resumen;
    }
}