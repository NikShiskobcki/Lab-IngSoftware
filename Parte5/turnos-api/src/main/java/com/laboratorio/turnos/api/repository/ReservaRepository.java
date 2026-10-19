package com.laboratorio.turnos.api.repository;

import com.laboratorio.turnos.api.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

// repositorio jpa para operaciones crud sobre reservas
@Repository
public interface ReservaRepository extends JpaRepository<Reserva, Integer> {
    List<Reserva> findByIdPersonal(Integer idPersonal);

    List<Reserva> findByIdPersonalAndFechaTurnoOrderByHoraTurnoAsc(Integer idPersonal, LocalDate fechaTurno);
}