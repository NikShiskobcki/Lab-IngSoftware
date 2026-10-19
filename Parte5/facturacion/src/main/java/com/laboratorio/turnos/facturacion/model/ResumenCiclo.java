package com.laboratorio.turnos.facturacion.model;

// resultado de una ejecucion del proceso de facturacion
public record ResumenCiclo(int encontrados, int facturados, int omitidos, int errores) {
}
