package com.laboratorio.turnos.api.service;

import com.laboratorio.turnos.api.dto.CrearReservaDTO;
import com.laboratorio.turnos.api.dto.mqtt.MqttReservaMessageDTO;
import com.laboratorio.turnos.api.dto.mqtt.MqttTurnoDTO;
import com.laboratorio.turnos.api.exception.ResourceNotFoundException;
import com.laboratorio.turnos.api.model.Reserva;
import com.laboratorio.turnos.api.repository.ReservaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// servicio con la logica de negocio de reservas
@Service
public class ReservaService {

    private final ReservaRepository reservaRepository;
    private final MqttPublisherService mqttPublisherService;

    public ReservaService(ReservaRepository reservaRepository, MqttPublisherService mqttPublisherService) {
        this.reservaRepository = reservaRepository;
        this.mqttPublisherService = mqttPublisherService;
    }

    // regitra la reserva y luego la envia a mosquito para validaciones
    public Reserva generarReserva(CrearReservaDTO dto) {
        Reserva reserva = new Reserva();
        reserva.setIdPersonal(dto.getIdPersonal());
        reserva.setIdEstablecimiento(dto.getIdEstablecimiento());
        reserva.setEmailSolicitante(dto.getEmailCliente());
        reserva.setTelefonoSolicitante(dto.getTelefonoCliente());
        reserva.setFechaTurno(dto.getFecha());
        reserva.setHoraTurno(dto.getHora());
        reserva.setDuracionMinutos(30);
        reserva.setFechaRegistro(LocalDateTime.now());
        reserva.setEstado("SOLICITADO");

        Reserva reservaGuardada = reservaRepository.save(reserva);

        MqttTurnoDTO turnoDTO = new MqttTurnoDTO(
                dto.getIdPersonal(),
                dto.getIdEstablecimiento(),
                dto.getEmailCliente(),
                dto.getTelefonoCliente(),
                dto.getFecha(),
                dto.getHora()
        );

        turnoDTO.setId(reservaGuardada.getId());

        MqttReservaMessageDTO mensajeDTO = new MqttReservaMessageDTO(
                "NUEVO",
                LocalDateTime.now(),
                turnoDTO
        );

        mqttPublisherService.publicarReserva(mensajeDTO);

        return reservaGuardada;
    }

    // publica la modificacion en mqtt, no toca la base de datos: el suscriptor valida y actualiza
    public Reserva actualizarReserva(Integer id, CrearReservaDTO dto) {
        Reserva reserva = reservaRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Reserva con id " + id + " no encontrada"
                        )
                );
        if ("Atendido".equalsIgnoreCase(reserva.getEstado())
                || "Facturado".equalsIgnoreCase(reserva.getEstado())) {
            throw new IllegalArgumentException(
                    "No se puede modificar una reserva atendida o facturada"
            );
        }

        reserva.setIdPersonal(dto.getIdPersonal());
        reserva.setIdEstablecimiento(dto.getIdEstablecimiento());
        reserva.setEmailSolicitante(dto.getEmailCliente());
        reserva.setTelefonoSolicitante(dto.getTelefonoCliente());
        reserva.setFechaTurno(dto.getFecha());
        reserva.setHoraTurno(dto.getHora());
        reserva.setEstado("SOLICITADO");

        Reserva reservaActualizada = reservaRepository.save(reserva);

        MqttTurnoDTO turnoDTO = new MqttTurnoDTO(
                dto.getIdPersonal(),
                dto.getIdEstablecimiento(),
                dto.getEmailCliente(),
                dto.getTelefonoCliente(),
                dto.getFecha(),
                dto.getHora()
        );

        turnoDTO.setId(reservaActualizada.getId());

        MqttReservaMessageDTO mensajeDTO = new MqttReservaMessageDTO(
                "ACTUALIZAR",
                LocalDateTime.now(),
                turnoDTO
        );

        mqttPublisherService.publicarReserva(mensajeDTO);

        return reservaActualizada;
    }

    // consulta todas las reservas o filtra por idPersonal
    public List<Reserva> listarTodas(Optional<Integer> idPersonal) {
        return idPersonal.map(reservaRepository::findByIdPersonal)
                .orElseGet(reservaRepository::findAll);
    }

    // consulta una reserva por su id
    public Optional<Reserva> buscarPorId(Integer id) {
        return reservaRepository.findById(id);
    }

    // elimina la reserva por su id
    public boolean eliminarReserva(Integer id) {
        Optional<Reserva> resultado = reservaRepository.findById(id);

        if (resultado.isEmpty()) {
            return false;
        }

        Reserva reserva = resultado.get();

        if ("Atendido".equalsIgnoreCase(reserva.getEstado())
                || "Facturado".equalsIgnoreCase(reserva.getEstado())) {
            throw new IllegalArgumentException(
                    "No se puede eliminar una reserva atendida o facturada"
            );
        }

        reservaRepository.delete(reserva);
        return true;
    }
}
