package com.laboratorio.turnos.facturacion.repository;

import com.laboratorio.turnos.facturacion.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

    Optional<Cliente> findByEmail(String email);
}
