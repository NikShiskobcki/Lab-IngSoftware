package com.laboratorio.turnos.facturacion.model;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDate;
import java.time.LocalTime;

// facturacion solo lee estos campos y cambia el estado: con @DynamicUpdate el update incluye solo lo que cambio.
@Entity
@Table(name = "reservas_turnos")
@DynamicUpdate
public class Reserva {

    @Id
    private Integer id;

    @Column(name = "id_personal", nullable = false)
    private Integer idPersonal;

    @Column(name = "email_solicitante", nullable = false, length = 100)
    private String emailSolicitante;

    @Column(name = "telefono_solicitante", nullable = false, length = 50)
    private String telefonoSolicitante;

    @Column(name = "fecha_turno", nullable = false)
    private LocalDate fechaTurno;

    @Column(name = "hora_turno", nullable = false)
    private LocalTime horaTurno;

    @Column(name = "estado", nullable = false, length = 30)
    private String estado;

    protected Reserva() {}

    public Integer getId() { return id; }
    public Integer getIdPersonal() { return idPersonal; }
    public String getEmailSolicitante() { return emailSolicitante; }
    public String getTelefonoSolicitante() { return telefonoSolicitante; }
    public LocalDate getFechaTurno() { return fechaTurno; }
    public LocalTime getHoraTurno() { return horaTurno; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
