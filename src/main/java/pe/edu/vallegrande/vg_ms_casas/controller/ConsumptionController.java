package pe.edu.vallegrande.vg_ms_casas.controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.vallegrande.vg_ms_casas.model.Consumption;
import pe.edu.vallegrande.vg_ms_casas.service.ConsumptionService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/consumption")
public class ConsumptionController {

    @Autowired
    private ConsumptionService consumptionService;

    @GetMapping
    public Flux<Consumption> getAll() {
        return consumptionService.findAll();
    }

    @GetMapping("/{id}")
    public Mono<Consumption> getById(@PathVariable Integer id) {
        return consumptionService.findById(id);
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> create(@RequestBody Consumption consumption) {
        return consumptionService.save(consumption)
                .map(saved -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "El consumo ha sido registrado correctamente.");
                    response.put("consumo", saved);
                    return ResponseEntity.ok(response);
                });
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> update(@PathVariable Integer id, @RequestBody Consumption consumption) {
        return consumptionService.update(id, consumption)
                .map(updated -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "El consumo ha sido actualizado correctamente.");
                    response.put("consumo", updated);
                    return ResponseEntity.ok(response);
                });
    }

    @PutMapping("/{id}/inactivar")
    public Mono<ResponseEntity<Map<String, String>>> delete(@PathVariable Integer id) {
        return consumptionService.delete(id)
                .thenReturn(createResponse("El consumo ha sido inactivado."));
    }

    @PutMapping("/{id}/restore")
    public Mono<ResponseEntity<Map<String, String>>> restore(@PathVariable Integer id) {
        return consumptionService.restore(id)
                .thenReturn(createResponse("El consumo ha sido restaurado."));
    }

    @GetMapping("/lista-activos")
    public Flux<Consumption> listActive() {
        return consumptionService.findActive();
    }

    @GetMapping("/lista-inactivos")
    public Flux<Consumption> listInactive() {
        return consumptionService.findInactive();
    }

    @GetMapping("/by-date-range")
    public Flux<Consumption> getByDateRange(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "activeOnly", defaultValue = "true") boolean activeOnly) {
        return consumptionService.findByDateRange(startDate, endDate, activeOnly);
    }

    private ResponseEntity<Map<String, String>> createResponse(String message) {
        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return ResponseEntity.ok(response);
    }
}