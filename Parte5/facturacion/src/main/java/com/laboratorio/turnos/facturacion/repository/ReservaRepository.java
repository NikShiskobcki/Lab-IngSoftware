package com.laboratorio.turnos.facturacion.repository;

import com.laboratorio.turnos.facturacion.model.Reserva;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReservaRepository extends JpaRepository<Reserva, Integer> {

    // ids de los turnos en un estado, del mas antiguo al mas nuevo
    @Query("select r.id from Reserva r where r.estado = :estado order by r.fechaTurno, r.horaTurno, r.id")
    List<Integer> buscarIdsPorEstado(@Param("estado") String estado);

    // bloquea el turno (select ... for update) solo si sigue en ese estado; vacio si otro proceso ya lo cambio
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reserva r where r.id = :id and r.estado = :estado")
    Optional<Reserva> bloquearPorIdYEstado(@Param("id") Integer id, @Param("estado") String estado);
}
