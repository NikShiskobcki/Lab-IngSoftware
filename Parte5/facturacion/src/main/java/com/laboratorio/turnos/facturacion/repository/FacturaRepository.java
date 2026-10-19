package com.laboratorio.turnos.facturacion.repository;

import com.laboratorio.turnos.facturacion.model.Factura;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FacturaRepository extends JpaRepository<Factura, Integer> {

    // factura del cliente para el periodo, bloqueada (for update)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Factura f where f.cliente.id = :idCliente and f.anio = :anio and f.mes = :mes")
    Optional<Factura> buscarYBloquear(@Param("idCliente") Integer idCliente,
                                      @Param("anio") Integer anio,
                                      @Param("mes") Integer mes);
}
