package com.laboratorio.turnos.api.personal;

import com.laboratorio.turnos.api.establecimiento.Establecimiento;
import com.laboratorio.turnos.api.establecimiento.EstablecimientoRepository;
import com.laboratorio.turnos.api.exception.ResourceNotFoundException;
import com.laboratorio.turnos.api.model.Reserva;
import com.laboratorio.turnos.api.personal.dto.AgendaResponse;
import com.laboratorio.turnos.api.personal.dto.DisponibilidadResponse;
import com.laboratorio.turnos.api.personal.dto.TurnoAgendaResponse;
import com.laboratorio.turnos.api.repository.ReservaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PersonalConsultaService {

    private static final int DURACION_TURNO_MINUTOS = 30;

    private final PersonalRepository personalRepository;
    private final ReservaRepository reservaRepository;
    private final EstablecimientoRepository establecimientoRepository;

    public PersonalConsultaService(
            PersonalRepository personalRepository,
            ReservaRepository reservaRepository,
            EstablecimientoRepository establecimientoRepository
    ) {
        this.personalRepository = personalRepository;
        this.reservaRepository = reservaRepository;
        this.establecimientoRepository = establecimientoRepository;
    }

    public AgendaResponse consultarAgenda(Long idPersonal, LocalDate fecha) {
        Personal personal = buscarPersonal(idPersonal);

        Integer idPersonalReserva = Math.toIntExact(idPersonal);

        List<TurnoAgendaResponse> turnos = reservaRepository
                .findByIdPersonalAndFechaTurnoOrderByHoraTurnoAsc(
                        idPersonalReserva,
                        fecha
                )
                .stream()
                .map(this::convertirTurno)
                .toList();

        return new AgendaResponse(
                personal.getId(),
                personal.getNombre(),
                fecha,
                turnos
        );
    }

    public DisponibilidadResponse consultarDisponibilidad(
            Long idPersonal,
            LocalDate fecha
    ) {
        Personal personal = buscarPersonal(idPersonal);

        if (personal.getEstado() != EstadoPersonal.ACTIVO) {
            return new DisponibilidadResponse(
                    personal.getId(),
                    personal.getNombre(),
                    fecha,
                    DURACION_TURNO_MINUTOS,
                    List.of()
            );
        }

        Establecimiento establecimiento = establecimientoRepository
                .findById(personal.getIdEstablecimiento())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Establecimiento del personal no encontrado"
                ));

        Integer idPersonalReserva = Math.toIntExact(idPersonal);

        List<Reserva> reservas = reservaRepository
                .findByIdPersonalAndFechaTurnoOrderByHoraTurnoAsc(
                        idPersonalReserva,
                        fecha
                );

        List<LocalTime> horariosDisponibles = new ArrayList<>();

        LocalTime horario = establecimiento.getHorarioApertura();
        LocalTime cierre = establecimiento.getHorarioCierre();

        while (!horario.plusMinutes(DURACION_TURNO_MINUTOS).isAfter(cierre)) {
            if (!estaOcupado(horario, reservas)) {
                horariosDisponibles.add(horario);
            }

            horario = horario.plusMinutes(DURACION_TURNO_MINUTOS);
        }

        return new DisponibilidadResponse(
                personal.getId(),
                personal.getNombre(),
                fecha,
                DURACION_TURNO_MINUTOS,
                horariosDisponibles
        );
    }

    private Personal buscarPersonal(Long idPersonal) {
        return personalRepository.findById(idPersonal)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Personal con id " + idPersonal + " no encontrado"
                ));
    }

    private boolean estaOcupado(
            LocalTime inicioHorario,
            List<Reserva> reservas
    ) {
        LocalTime finHorario =
                inicioHorario.plusMinutes(DURACION_TURNO_MINUTOS);

        return reservas.stream()
                .filter(reserva ->
                        "CONFIRMADO".equalsIgnoreCase(reserva.getEstado())
                )
                .anyMatch(reserva -> {
                    LocalTime inicioReserva = reserva.getHoraTurno();

                    int duracionReserva =
                            reserva.getDuracionMinutos() != null
                                    ? reserva.getDuracionMinutos()
                                    : DURACION_TURNO_MINUTOS;

                    LocalTime finReserva =
                            inicioReserva.plusMinutes(duracionReserva);

                    return inicioHorario.isBefore(finReserva)
                            && finHorario.isAfter(inicioReserva);
                });
    }

    private TurnoAgendaResponse convertirTurno(Reserva reserva) {
        return new TurnoAgendaResponse(
                reserva.getId(),
                reserva.getHoraTurno(),
                reserva.getDuracionMinutos(),
                reserva.getEstado(),
                reserva.getEmailSolicitante(),
                reserva.getTelefonoSolicitante()
        );
    }
}