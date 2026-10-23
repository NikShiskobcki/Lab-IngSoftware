package com.laboratorio.turnos.api.integracion;

import com.laboratorio.turnos.api.establecimiento.Establecimiento;
import com.laboratorio.turnos.api.establecimiento.EstablecimientoRepository;
import com.laboratorio.turnos.api.model.Reserva;
import com.laboratorio.turnos.api.personal.EstadoPersonal;
import com.laboratorio.turnos.api.personal.Personal;
import com.laboratorio.turnos.api.personal.PersonalRepository;
import com.laboratorio.turnos.api.repository.ReservaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Repositorios de turnos-api contra MariaDB")
class RepositoriosApiIntegracionTest extends IntegracionJpaBaseTest {

    @Autowired EstablecimientoRepository establecimientos;
    @Autowired PersonalRepository personal;
    @Autowired ReservaRepository reservas;

    private Establecimiento guardarEstablecimiento() {
        Establecimiento e = new Establecimiento();
        e.setNombreComercial("Clinica Test");
        e.setDireccion("Calle 1");
        e.setTelefono("099111111");
        e.setCorreoElectronico("a@b.com");
        e.setHorarioApertura(LocalTime.of(8, 0));
        e.setHorarioCierre(LocalTime.of(18, 0));
        return establecimientos.saveAndFlush(e);
    }

    private Personal guardarPersonal(Long idEstablecimiento, EstadoPersonal estado) {
        return personal.saveAndFlush(new Personal(null, idEstablecimiento, "Dra. Ana Perez", "Clinica",
                new BigDecimal("1500.00"), 30, estado));
    }

    private Reserva nuevaReserva(int idPersonal, String fecha, String hora) {
        Reserva r = new Reserva();
        r.setIdPersonal(idPersonal);
        r.setEmailSolicitante("c@d.com");
        r.setTelefonoSolicitante("099222222");
        r.setFechaTurno(LocalDate.parse(fecha));
        r.setHoraTurno(LocalTime.parse(hora));
        r.setFechaRegistro(LocalDateTime.now());
        return r;
    }

    @Test
    @DisplayName("Alta de establecimiento: queda persistido en sus columnas")
    void altaDeEstablecimiento() {
        Establecimiento e = guardarEstablecimiento();

        assertThat(e.getId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT nombre_comercial FROM establecimientos WHERE id = ?",
                String.class, e.getId())).isEqualTo("Clinica Test");
        assertThat(jdbc.queryForObject("SELECT TIME_FORMAT(horario_cierre, '%H:%i') FROM establecimientos WHERE id = ?",
                String.class, e.getId())).isEqualTo("18:00");
    }

    @Test
    @DisplayName("Alta de personal: el estado se guarda como texto y se lee de vuelta como enum")
    void altaDePersonalConEstado() {
        Establecimiento e = guardarEstablecimiento();
        Personal p = guardarPersonal(e.getId(), EstadoPersonal.INACTIVO);
        em.clear();

        assertThat(jdbc.queryForObject("SELECT estado FROM personal WHERE id = ?", String.class, p.getId()))
                .isEqualTo("INACTIVO");
        Personal leido = personal.findById(p.getId()).orElseThrow();
        assertThat(leido.getEstado()).isEqualTo(EstadoPersonal.INACTIVO);
        assertThat(leido.getCostoConsulta()).isEqualByComparingTo("1500.00");
        assertThat(leido.getDuracionEstandarMinutos()).isEqualTo(30);
    }

    @Test
    @DisplayName("No se puede guardar personal de un establecimiento inexistente")
    void personalSinEstablecimiento() {
        assertThrows(DataIntegrityViolationException.class,
                () -> guardarPersonal(999L, EstadoPersonal.ACTIVO));
    }

    @Test
    @DisplayName("Borrar un establecimiento borra tambien su personal (ON DELETE CASCADE)")
    void borrarEstablecimientoBorraPersonal() {
        Establecimiento e = guardarEstablecimiento();
        Personal p = guardarPersonal(e.getId(), EstadoPersonal.ACTIVO);

        establecimientos.deleteById(e.getId());
        establecimientos.flush();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM personal WHERE id = ?", Integer.class, p.getId()))
                .isEqualTo(0);
    }

    @Test
    @DisplayName("No se puede borrar un establecimiento si su personal tiene reservas (RESTRICT)")
    void borrarEstablecimientoConReservas() {
        Establecimiento e = guardarEstablecimiento();
        Personal p = guardarPersonal(e.getId(), EstadoPersonal.ACTIVO);
        reservas.saveAndFlush(nuevaReserva(p.getId().intValue(), "2030-01-01", "10:00"));

        assertThrows(DataIntegrityViolationException.class, () -> {
            establecimientos.deleteById(e.getId());
            establecimientos.flush();
        });
    }

    @Test
    @DisplayName("Reservas de un profesional en un dia: solo las suyas y ordenadas por hora")
    void reservasPorProfesionalYDia() {
        Establecimiento e = guardarEstablecimiento();
        int p1 = guardarPersonal(e.getId(), EstadoPersonal.ACTIVO).getId().intValue();
        int p2 = guardarPersonal(e.getId(), EstadoPersonal.ACTIVO).getId().intValue();
        reservas.save(nuevaReserva(p1, "2030-01-01", "11:00"));
        reservas.save(nuevaReserva(p1, "2030-01-01", "09:00"));
        reservas.save(nuevaReserva(p1, "2030-01-01", "10:00"));
        reservas.save(nuevaReserva(p1, "2030-01-02", "09:00"));   // otro dia
        reservas.saveAndFlush(nuevaReserva(p2, "2030-01-01", "08:00"));   // otro profesional

        List<Reserva> delDia = reservas.findByIdPersonalAndFechaTurnoOrderByHoraTurnoAsc(p1, LocalDate.of(2030, 1, 1));

        assertThat(delDia).extracting(Reserva::getHoraTurno)
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0));
        assertThat(reservas.findByIdPersonal(p1)).hasSize(4);
    }

    @Test
    @DisplayName("Una reserva nueva se guarda SOLICITADO, de 30 min y sin establecimiento")
    void valoresPorDefectoDeUnaReserva() {
        Establecimiento e = guardarEstablecimiento();
        int p = guardarPersonal(e.getId(), EstadoPersonal.ACTIVO).getId().intValue();

        Integer id = reservas.saveAndFlush(nuevaReserva(p, "2030-01-01", "10:00")).getId();

        assertThat(jdbc.queryForObject("SELECT estado FROM reservas_turnos WHERE id = ?", String.class, id))
                .isEqualTo("SOLICITADO");
        assertThat(jdbc.queryForObject("SELECT duracion_minutos FROM reservas_turnos WHERE id = ?", Integer.class, id))
                .isEqualTo(30);
        assertThat(jdbc.queryForObject("SELECT id_establecimiento FROM reservas_turnos WHERE id = ?", Integer.class, id))
                .isNull();
    }

    @Test
    @DisplayName("No se puede guardar una reserva de un profesional inexistente")
    void reservaDeProfesionalInexistente() {
        assertThrows(DataIntegrityViolationException.class,
                () -> reservas.saveAndFlush(nuevaReserva(999, "2030-01-01", "10:00")));
    }
}