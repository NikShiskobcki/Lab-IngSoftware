package com.laboratorio.turnos.api.personal;

import com.laboratorio.turnos.api.establecimiento.EstablecimientoRepository;
import com.laboratorio.turnos.api.exception.ResourceNotFoundException;
import com.laboratorio.turnos.api.personal.dto.PersonalInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/personal")
@Tag(name="Personal")
public class PersonalController {
    private final PersonalRepository repository;
    private final EstablecimientoRepository establecimientoRepository;

    public PersonalController(PersonalRepository repository, EstablecimientoRepository establecimientoRepository){
        this.repository = repository;
        this.establecimientoRepository = establecimientoRepository;
    }

    @Operation(summary = "Registrar personal")
    @PostMapping
    public ResponseEntity<Personal> crear(@Valid @RequestBody PersonalInput input){
        validarEstablecimientoExiste(input.getIdEstablecimiento());
        Personal p = new Personal();
        copiar(input,p);
        Personal guardado = repository.save(p);
        return ResponseEntity.status(HttpStatus.CREATED).body(guardado);
    }

    @Operation(summary="Listar personal")
    @GetMapping
    public ResponseEntity<List<Personal>> listar(){
        return ResponseEntity.ok(repository.findAll());
    }

    @Operation(summary= "Obtener personal por id")
    @GetMapping("/{id}")
    public ResponseEntity<Personal> obtener(@PathVariable Long id){
        Personal p = repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Personal con id " + id+ " no encontrado"));
        return ResponseEntity.ok(p);
    }

    @Operation(summary="Acualizar datos de personal")
    @PutMapping("/{id}")
    public ResponseEntity<Personal> actualizar(@PathVariable Long id, @Valid @RequestBody PersonalInput input){
        Personal p = repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Personal con id " + id+ " no encontrado"));
        validarEstablecimientoExiste(input.getIdEstablecimiento());
        copiar(input,p);
        return ResponseEntity.ok(repository.save(p));
    }

    @Operation(summary="Eliminar personal")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id){
        if (!repository.existsById(id)){
            throw new ResourceNotFoundException("Personal con id " + id + " no encontrado");
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }



    //funciones auxiliares

    private void validarEstablecimientoExiste(Long idEstablecimiento){
        if(!establecimientoRepository.existsById(idEstablecimiento)){
            throw new IllegalArgumentException("El establecimiento con id "+idEstablecimiento+" no existe");
        }
    }

    private void copiar(PersonalInput input, Personal p){
        p.setIdEstablecimiento(input.getIdEstablecimiento());
        p.setNombre(input.getNombre());
        p.setEspecialidad(input.getEspecialidad());
        p.setCostoConsulta(input.getCostoConsulta());
        p.setDuracionEstandarMinutos(input.getDuracionEstandarMinutos());
        if (input.getEstado() != null) {
            p.setEstado(input.getEstado());
        }
    }




}
