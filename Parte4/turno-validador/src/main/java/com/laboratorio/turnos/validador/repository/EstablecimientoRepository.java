package com.laboratorio.turnos.validador.repository;

import com.laboratorio.turnos.validador.model.Establecimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// repositorio jpa para establecimientos
@Repository
public interface EstablecimientoRepository extends JpaRepository<Establecimiento, Integer> {
}
