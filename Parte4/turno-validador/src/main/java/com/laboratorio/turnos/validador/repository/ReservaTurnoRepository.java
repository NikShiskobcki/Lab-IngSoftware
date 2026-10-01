package com.laboratorio.turnos.validador.repository;

import com.laboratorio.turnos.validador.model.ReservaTurno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// repositorio jpa para reservas_turnos
@Repository
public interface ReservaTurnoRepository extends JpaRepository<ReservaTurno, Integer> {

    // obtener turnos por una lista de estados
    List<ReservaTurno> findByEstadoIn(List<String> estados);

    // buscar turnos para el mismo profesional, fecha y hora
    List<ReservaTurno> findByIdPersonalAndFechaTurnoAndHoraTurno(Integer idPersonal, LocalDate fechaTurno, LocalTime horaTurno);
}
