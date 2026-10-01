package com.laboratorio.turnos.validador.repository;

import com.laboratorio.turnos.validador.model.Personal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// repositorio jpa para personal
@Repository
public interface PersonalRepository extends JpaRepository<Personal, Integer> {
}
