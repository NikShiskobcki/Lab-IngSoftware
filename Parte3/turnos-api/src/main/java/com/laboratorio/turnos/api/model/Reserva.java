package com.laboratorio.turnos.api.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// entidad jpa para la tabla reservas_turnos
@Entity
@Table(name = "reservas_turnos")
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    @Column(name = "duracion_minutos", nullable = false)
    private Integer duracionMinutos = 30;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @Column(name = "estado", nullable = false, length = 30)
    private String estado = "CONFIRMADO";

    public Reserva() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getIdPersonal() { return idPersonal; }
    public void setIdPersonal(Integer idPersonal) { this.idPersonal = idPersonal; }

    public String getEmailSolicitante() { return emailSolicitante; }
    public void setEmailSolicitante(String emailSolicitante) { this.emailSolicitante = emailSolicitante; }

    public String getTelefonoSolicitante() { return telefonoSolicitante; }
    public void setTelefonoSolicitante(String telefonoSolicitante) { this.telefonoSolicitante = telefonoSolicitante; }

    public LocalDate getFechaTurno() { return fechaTurno; }
    public void setFechaTurno(LocalDate fechaTurno) { this.fechaTurno = fechaTurno; }

    public LocalTime getHoraTurno() { return horaTurno; }
    public void setHoraTurno(LocalTime horaTurno) { this.horaTurno = horaTurno; }

    public Integer getDuracionMinutos() { return duracionMinutos; }
    public void setDuracionMinutos(Integer duracionMinutos) { this.duracionMinutos = duracionMinutos; }

    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}