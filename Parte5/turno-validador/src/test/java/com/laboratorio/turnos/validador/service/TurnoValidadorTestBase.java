package com.laboratorio.turnos.validador.service;

import com.laboratorio.turnos.validador.model.Personal;
import com.laboratorio.turnos.validador.model.ReservaTurno;
import com.laboratorio.turnos.validador.mqtt.ValidadorMqtt;
import com.laboratorio.turnos.validador.repository.EstablecimientoRepository;
import com.laboratorio.turnos.validador.repository.PersonalRepository;
import com.laboratorio.turnos.validador.repository.ReservaTurnoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Base compartida por los tests unitarios del validador (US-52 y US-53).
 * Repositorios y cliente MQTT son mocks: no hay base de datos ni broker.
 * La cola de eventos (ColaValidacion) es la real porque es una estructura en memoria.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
abstract class TurnoValidadorTestBase {

    protected static final int ID_PERSONAL = 1;
    protected static final int ID_ESTABLECIMIENTO = 1;

    @Mock
    protected ReservaTurnoRepository reservaRepo;
    @Mock
    protected PersonalRepository personalRepo;
    @Mock
    protected EstablecimientoRepository establecimientoRepo;
    @Mock
    protected ValidadorMqtt mqtt;

    protected ColaValidacion cola;
    protected TurnoValidadorService service;

    @BeforeEach
    void baseSetUp() {
        cola = new ColaValidacion();
        service = new TurnoValidadorService(reservaRepo, personalRepo, establecimientoRepo, cola, mqtt);

        // el servicio registra la publicacion MQTT para "despues del commit": simulamos una transaccion activa
        TransactionSynchronizationManager.initSynchronization();

        // escenario base: profesional ACTIVO del establecimiento 1, que existe
        when(personalRepo.findById(ID_PERSONAL)).thenReturn(Optional.of(personal("ACTIVO", ID_ESTABLECIMIENTO)));
        when(establecimientoRepo.existsById(ID_ESTABLECIMIENTO)).thenReturn(true);
        when(reservaRepo.findByIdPersonalAndFechaTurno(any(), any())).thenReturn(new ArrayList<>());
        when(reservaRepo.save(any(ReservaTurno.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void baseTearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ---------- fabricas de datos ----------

    protected Personal personal(String estado, int idEstablecimiento) {
        Personal p = new Personal();
        p.setId(ID_PERSONAL);
        p.setNombre("Dra. Ana Perez");
        p.setEstado(estado);
        p.setIdEstablecimiento(idEstablecimiento);
        return p;
    }

    protected ReservaTurno turno(int id, LocalDate fecha, LocalTime hora, String estado) {
        ReservaTurno t = new ReservaTurno();
        t.setId(id);
        t.setIdPersonal(ID_PERSONAL);
        t.setIdEstablecimiento(ID_ESTABLECIMIENTO);
        t.setEmailSolicitante("cliente@correo.com");
        t.setTelefonoSolicitante("099123456");
        t.setFechaTurno(fecha);
        t.setHoraTurno(hora);
        t.setDuracionMinutos(30);
        t.setFechaRegistro(LocalDateTime.now());
        t.setEstado(estado);
        return t;
    }

    protected LocalDate futuro() {
        return LocalDate.now().plusDays(5);
    }

    /** Deja el turno en la cola y en el repositorio, como si hubiera llegado por MQTT. */
    protected ReservaTurno recibir(ReservaTurno t) {
        when(reservaRepo.findById(t.getId())).thenReturn(Optional.of(t));
        cola.agregar(t.getId());
        return t;
    }

    /** Declara los turnos que el profesional ya tiene ese dia. */
    protected void turnosExistentes(LocalDate fecha, ReservaTurno... existentes) {
        when(reservaRepo.findByIdPersonalAndFechaTurno(eq(ID_PERSONAL), eq(fecha)))
                .thenReturn(new ArrayList<>(List.of(existentes)));
    }

    /** Simula el commit de la transaccion: dispara los afterCommit registrados por el servicio. */
    protected void commit() {
        new ArrayList<>(TransactionSynchronizationManager.getSynchronizations())
                .forEach(TransactionSynchronization::afterCommit);
    }
}
