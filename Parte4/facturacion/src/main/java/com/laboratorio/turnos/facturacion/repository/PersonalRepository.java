package com.laboratorio.turnos.facturacion.repository;

import com.laboratorio.turnos.facturacion.model.Personal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalRepository extends JpaRepository<Personal, Integer> {
}
