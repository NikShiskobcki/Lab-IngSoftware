package com.laboratorio.turnos.validador.service;

import com.laboratorio.turnos.validador.model.Personal;
import com.laboratorio.turnos.validador.model.ReservaTurno;
import com.laboratorio.turnos.validador.repository.EstablecimientoRepository;
import com.laboratorio.turnos.validador.repository.PersonalRepository;
import com.laboratorio.turnos.validador.repository.ReservaTurnoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// servicio asincrono que ejecuta cada 1 minuto para validar turnos
@Service
public class TurnoValidadorService {

    private static final Logger logger = LoggerFactory.getLogger(TurnoValidadorService.class);

    private final ReservaTurnoRepository reservaTurnoRepository;
    private final PersonalRepository personalRepository;
    private final EstablecimientoRepository establecimientoRepository;

    public TurnoValidadorService(ReservaTurnoRepository reservaTurnoRepository,
                                  PersonalRepository personalRepository,
                                  EstablecimientoRepository establecimientoRepository) {
        this.reservaTurnoRepository = reservaTurnoRepository;
        this.personalRepository = personalRepository;
        this.establecimientoRepository = establecimientoRepository;
    }

    // us-35: revisar turnos cada 1 minuto
    @Scheduled(fixedRateString = "${validador.intervalo-ms:60000}")
    @Transactional
    public void revisarTurnosNuevos() {
        try {
            logger.info("iniciando ciclo de revision de turnos nuevos");

            List<String> estadosPendientes = List.of("SOLICITADO", "PENDIENTE", "CONFIRMADO");
            List<ReservaTurno> nuevosTurnos = reservaTurnoRepository.findByEstadoIn(estadosPendientes);

            if (nuevosTurnos.isEmpty()) {
                logger.info("no hay turnos pendientes para validar");
                return;
            }

            logger.info("se encontraron {} turnos para validar", nuevosTurnos.size());

            for (ReservaTurno turno : nuevosTurnos) {
                procesarValidacionesTurno(turno);
            }

            logger.info("ciclo de revision de turnos completado");
        } catch (Exception e) {
            logger.warn("aviso durante el ciclo de revision: {}", e.getMessage());
        }
    }

    private void procesarValidacionesTurno(ReservaTurno turno) {
        logger.info("validando turno id={} para profesional id={}", turno.getId(), turno.getIdPersonal());

        // us-36: validacion de establecimiento y personal
        Optional<Personal> personalOpt = personalRepository.findById(turno.getIdPersonal());
        if (personalOpt.isEmpty()) {
            logger.warn("turno id={} rechazado: el personal no existe", turno.getId());
            turno.setEstado("Rechazado/Solicitud No Valida");
            reservaTurnoRepository.save(turno);
            return;
        }

        Personal personal = personalOpt.get();
        if (!"ACTIVO".equalsIgnoreCase(personal.getEstado())) {
            logger.warn("turno id={} rechazado: personal inactivo", turno.getId());
            turno.setEstado("Rechazado/Solicitud No Valida");
            reservaTurnoRepository.save(turno);
            return;
        }

        Integer idEstablecimiento = (turno.getIdEstablecimiento() != null)
                ? turno.getIdEstablecimiento()
                : personal.getIdEstablecimiento();

        if (!establecimientoRepository.existsById(idEstablecimiento)) {
            logger.warn("turno id={} rechazado: establecimiento id={} no existe", turno.getId(), idEstablecimiento);
            turno.setEstado("Rechazado/Solicitud No Valida");
            reservaTurnoRepository.save(turno);
            return;
        }

        if (turno.getIdEstablecimiento() != null && !turno.getIdEstablecimiento().equals(personal.getIdEstablecimiento())) {
            logger.warn("turno id={} rechazado: personal no pertenece al establecimiento", turno.getId());
            turno.setEstado("Rechazado/Solicitud No Valida");
            reservaTurnoRepository.save(turno);
            return;
        }

        // us-37: validar fecha no pasada
        LocalDateTime fechaHoraTurno = LocalDateTime.of(turno.getFechaTurno(), turno.getHoraTurno());
        if (fechaHoraTurno.isBefore(LocalDateTime.now())) {
            logger.warn("turno id={} rechazado: fecha u hora en el pasado ({})", turno.getId(), fechaHoraTurno);
            turno.setEstado("Rechazado/Solicitud No Valida");
            reservaTurnoRepository.save(turno);
            return;
        }

        // us-38: verificar horario disponible
        List<ReservaTurno> coincidentes = reservaTurnoRepository.findByIdPersonalAndFechaTurnoAndHoraTurno(
                turno.getIdPersonal(),
                turno.getFechaTurno(),
                turno.getHoraTurno()
        );

        boolean horarioOcupado = coincidentes.stream()
                .filter(otro -> !otro.getId().equals(turno.getId()))
                .anyMatch(otro -> {
                    String est = otro.getEstado();
                    if (est == null) return false;
                    return est.equalsIgnoreCase("Agendado")
                            || est.equalsIgnoreCase("Atendido")
                            || est.equalsIgnoreCase("Facturado");
                });

        if (horarioOcupado) {
            logger.warn("turno id={} rechazado: horario ocupado", turno.getId());
            turno.setEstado("Rechazado/Turno Ocupado");
        } else {
            logger.info("turno id={} validado exitosamente: seteando estado Agendado", turno.getId());
            turno.setEstado("Agendado");
        }

        reservaTurnoRepository.save(turno);
    }
}
