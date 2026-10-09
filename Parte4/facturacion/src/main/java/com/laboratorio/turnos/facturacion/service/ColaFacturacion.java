package com.laboratorio.turnos.facturacion.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class ColaFacturacion {

    private final Queue<Integer> pendientes = new ConcurrentLinkedQueue<>();

    public void agregar(Integer idReserva) {
        pendientes.offer(idReserva);
    }

    public List<Integer> retirarPendientes() {
        List<Integer> ids = new ArrayList<>();
        Integer id;

        while ((id = pendientes.poll()) != null) {
            ids.add(id);
        }

        return ids;
    }
}