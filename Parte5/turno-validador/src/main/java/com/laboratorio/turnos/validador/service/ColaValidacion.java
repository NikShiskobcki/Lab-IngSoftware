package com.laboratorio.turnos.validador.service;

import org.springframework.stereotype.Component;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class ColaValidacion {

    private final Queue<Integer> pendientes = new ConcurrentLinkedQueue<>();

    public void agregar(Integer idReserva) {
        pendientes.offer(idReserva);
    }

    public Integer siguiente() {
        return pendientes.poll();
    }
}