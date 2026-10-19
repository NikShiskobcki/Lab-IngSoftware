package com.laboratorio.turnos.api.establecimiento;

import com.laboratorio.turnos.api.establecimiento.dto.EstablecimientoInput;
import com.laboratorio.turnos.api.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/establecimientos")
@Tag(name = "Establecimientos")
public class EstablecimientoController {

    private final EstablecimientoRepository repository;

    public EstablecimientoController(EstablecimientoRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "Crear un nuevo establecimiento")
    @PostMapping
    public ResponseEntity<Establecimiento> crear(@Valid @RequestBody EstablecimientoInput input) {
        Establecimiento e = new Establecimiento();
        copiar(input, e);
        Establecimiento guardado = repository.save(e);
        return ResponseEntity.status(HttpStatus.CREATED).body(guardado);
    }

    @Operation(summary = "Listar establecimientos")
    @GetMapping
    public ResponseEntity<List<Establecimiento>> listar() {
        return ResponseEntity.ok(repository.findAll());
    }

    @Operation(summary = "Obtener un establecimiento por id")
    @GetMapping("/{id}")
    public ResponseEntity<Establecimiento> obtener(@PathVariable Long id) {
        Establecimiento e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Establecimiento con id " + id + " no encontrado"));
        return ResponseEntity.ok(e);
    }

    @Operation(summary = "Actualizar un establecimiento")
    @PutMapping("/{id}")
    public ResponseEntity<Establecimiento> actualizar(@PathVariable Long id, @Valid @RequestBody EstablecimientoInput input) {
        Establecimiento e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Establecimiento con id " + id + " no encontrado"));
        copiar(input, e);
        return ResponseEntity.ok(repository.save(e));
    }

    @Operation(summary = "Eliminar un establecimiento")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Establecimiento con id " + id + " no encontrado");
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void copiar(EstablecimientoInput input, Establecimiento e) {
        if (input.getHorarioCierre() != null && input.getHorarioApertura() != null
                && !input.getHorarioCierre().isAfter(input.getHorarioApertura())) {
            throw new IllegalArgumentException("horarioCierre debe ser posterior a horarioApertura");
        }
        e.setNombreComercial(input.getNombreComercial());
        e.setDireccion(input.getDireccion());
        e.setTelefono(input.getTelefono());
        e.setCorreoElectronico(input.getCorreoElectronico());
        e.setHorarioApertura(input.getHorarioApertura());
        e.setHorarioCierre(input.getHorarioCierre());
    }
}
