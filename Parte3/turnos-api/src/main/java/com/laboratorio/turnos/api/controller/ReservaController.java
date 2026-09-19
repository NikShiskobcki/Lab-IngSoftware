package com.laboratorio.turnos.api.controller;

import com.laboratorio.turnos.api.dto.CrearReservaDTO;
import com.laboratorio.turnos.api.model.Reserva;
import com.laboratorio.turnos.api.service.ReservaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// controlador rest para reservas
@RestController
@RequestMapping("/reservas")
@Tag(name = "Reservas", description = "gestion de reservas de turnos")
public class ReservaController {

    private final ReservaService reservaService;

    public ReservaController(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    // generar reserva enviando evento a mqtt
    @PostMapping
    @Operation(summary = "generar nueva reserva", description = "envia la reserva a mosquitto para que el suscriptor valide y guarde")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "solicitud enviada a procesamiento"),
            @ApiResponse(responseCode = "400", description = "datos invalidos")
    })
    public ResponseEntity<Map<String, Object>> generarReserva(@Valid @RequestBody CrearReservaDTO dto) {
        reservaService.generarReserva(dto);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SOLICITUD_ENVIADA");
        response.put("mensaje", "reserva enviada a mosquitto para ser validada y persistida");
        response.put("datos", dto);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // consultar todas las reservas o filtrar por personal
    @GetMapping
    @Operation(summary = "listar reservas", description = "obtiene las reservas registradas")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "lista de reservas obtenida")
    })
    public ResponseEntity<List<Reserva>> listarReservas(
            @Parameter(description = "id opcional del personal para filtrar")
            @RequestParam(required = false) Integer idPersonal) {
        List<Reserva> reservas = reservaService.listarTodas(Optional.ofNullable(idPersonal));
        return ResponseEntity.ok(reservas);
    }

    // consultar reserva individual por id
    @GetMapping("/{id}")
    @Operation(summary = "consultar reserva por id", description = "retorna el detalle de una reserva")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "reserva encontrada"),
            @ApiResponse(responseCode = "404", description = "reserva no encontrada")
    })
    public ResponseEntity<?> buscarPorId(@PathVariable Integer id) {
        return reservaService.buscarPorId(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> {
                    Map<String, Object> error = new HashMap<>();
                    error.put("status", 404);
                    error.put("mensaje", "no existe reserva con id: " + id);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
                });
    }

    // eliminar reserva por id
    @DeleteMapping("/{id}")
    @Operation(summary = "eliminar reserva", description = "elimina una reserva por su id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "reserva eliminada"),
            @ApiResponse(responseCode = "404", description = "reserva no encontrada")
    })
    public ResponseEntity<?> eliminarReserva(@PathVariable Integer id) {
        boolean eliminada = reservaService.eliminarReserva(id);
        if (eliminada) {
            return ResponseEntity.noContent().build();
        } else {
            Map<String, Object> error = new HashMap<>();
            error.put("status", 404);
            error.put("mensaje", "no existe reserva con id: " + id + " para eliminar");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }
}
