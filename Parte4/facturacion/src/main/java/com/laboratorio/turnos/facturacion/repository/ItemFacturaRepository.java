package com.laboratorio.turnos.facturacion.repository;

import com.laboratorio.turnos.facturacion.model.ItemFactura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface ItemFacturaRepository extends JpaRepository<ItemFactura, Integer> {

    // el total de una factura es siempre la suma de sus items
    @Query("select coalesce(sum(i.monto), 0) from ItemFactura i where i.factura.id = :idFactura")
    BigDecimal sumarMontos(@Param("idFactura") Integer idFactura);
}
