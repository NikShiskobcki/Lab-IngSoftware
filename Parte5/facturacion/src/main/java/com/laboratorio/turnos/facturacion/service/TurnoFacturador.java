package com.laboratorio.turnos.facturacion.service;

import com.laboratorio.turnos.facturacion.model.Cliente;
import com.laboratorio.turnos.facturacion.model.Factura;
import com.laboratorio.turnos.facturacion.model.ItemFactura;
import com.laboratorio.turnos.facturacion.model.Personal;
import com.laboratorio.turnos.facturacion.model.Reserva;
import com.laboratorio.turnos.facturacion.repository.ClienteRepository;
import com.laboratorio.turnos.facturacion.repository.FacturaRepository;
import com.laboratorio.turnos.facturacion.repository.ItemFacturaRepository;
import com.laboratorio.turnos.facturacion.repository.PersonalRepository;
import com.laboratorio.turnos.facturacion.repository.ReservaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

// factura un unico turno en una unica transaccion (bean aparte para que @Transactional pase por el proxy)
@Service
public class TurnoFacturador {

    private static final Logger logger = LoggerFactory.getLogger(TurnoFacturador.class);

    public static final String ESTADO_ATENDIDO = "Atendido";
    public static final String ESTADO_FACTURADO = "Facturado";

    private final ReservaRepository reservas;
    private final PersonalRepository personal;
    private final ClienteRepository clientes;
    private final FacturaRepository facturas;
    private final ItemFacturaRepository items;

    public TurnoFacturador(ReservaRepository reservas, PersonalRepository personal, ClienteRepository clientes,
                           FacturaRepository facturas, ItemFacturaRepository items) {
        this.reservas = reservas;
        this.personal = personal;
        this.clientes = clientes;
        this.facturas = facturas;
        this.items = items;
    }

    // us-41/us-42: item de factura + cambio de estado en una unica transaccion.
    // devuelve false si el turno ya no estaba atendido (no se factura).
    // cualquier excepcion revierte todo: el turno queda Atendido y se reintenta en el proximo ciclo.
    @Transactional
    public boolean facturar(int idTurno) {
        Optional<Reserva> opt = reservas.bloquearPorIdYEstado(idTurno, ESTADO_ATENDIDO);
        if (opt.isEmpty()) {
            logger.info("turno id={} omitido: ya no esta en estado Atendido", idTurno);
            return false;
        }
        Reserva turno = opt.get();

        Personal profesional = personal.findById(turno.getIdPersonal()).orElseThrow(() ->
                new IllegalStateException("el personal id=" + turno.getIdPersonal()
                        + " del turno id=" + idTurno + " no existe"));

        // el cliente se identifica por email (sin distinguir mayusculas ni espacios)
        String email = turno.getEmailSolicitante().trim().toLowerCase(Locale.ROOT);
        Cliente cliente = clientes.findByEmail(email).orElseGet(() ->
                clientes.save(new Cliente(email, turno.getTelefonoSolicitante(), LocalDateTime.now())));

        // el mes de la factura es el del turno, no el de ejecucion del proceso
        int anio = turno.getFechaTurno().getYear();
        int mes = turno.getFechaTurno().getMonthValue();
        Factura factura = facturas.buscarYBloquear(cliente.getId(), anio, mes).orElseGet(() ->
                facturas.save(new Factura(cliente, anio, mes, LocalDateTime.now())));

        String descripcion = String.format("Turno #%d - %s (%s) - %s %s",
                turno.getId(), profesional.getNombre(), profesional.getEspecialidad(),
                turno.getFechaTurno(), turno.getHoraTurno().toString().substring(0, 5));
        items.save(new ItemFactura(factura, turno, descripcion, profesional.getCostoConsulta()));

        // el total se recalcula como la suma de los items
        factura.setTotal(items.sumarMontos(factura.getId()));

        // el estado cambia recien despues de agregar el item; se persiste al commit
        turno.setEstado(ESTADO_FACTURADO);

        logger.info("turno id={} facturado: cliente={}, factura id={} ({}/{}), monto={}",
                turno.getId(), email, factura.getId(), String.format("%02d", mes), anio, profesional.getCostoConsulta());
        return true;
    }
}
