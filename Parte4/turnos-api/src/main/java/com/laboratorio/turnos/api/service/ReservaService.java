package com.laboratorio.turnos.api.service;

import com.laboratorio.turnos.api.dto.CrearReservaDTO;
import com.laboratorio.turnos.api.dto.mqtt.MqttReservaMessageDTO;
import com.laboratorio.turnos.api.dto.mqtt.MqttTurnoDTO;
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

    // publica en mqtt para que el suscriptor valide y persista
    public void generarReserva(CrearReservaDTO dto) {
        MqttTurnoDTO turnoDTO = new MqttTurnoDTO(
                dto.getIdPersonal(),
                dto.getEmailCliente(),
                dto.getTelefonoCliente(),
                dto.getFecha(),
                dto.getHora()
        );

        MqttReservaMessageDTO mensajeDTO = new MqttReservaMessageDTO(
                "NUEVO",
                LocalDateTime.now(),
                turnoDTO
        );

        mqttPublisherService.publicarReserva(mensajeDTO);
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
        if (reservaRepository.existsById(id)) {
            reservaRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
