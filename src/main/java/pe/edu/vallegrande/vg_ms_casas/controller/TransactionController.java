package pe.edu.vallegrande.vg_ms_casas.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.vallegrande.vg_ms_casas.model.Transaction;
import pe.edu.vallegrande.vg_ms_casas.service.TransactionService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/transactions")
public class TransactionController {

    @Autowired
    private TransactionService transactionService;

    @GetMapping
    public Flux<Transaction> getAll() {
        return transactionService.findAll();
    }

    @GetMapping("/{id}")
    public Mono<Transaction> getById(@PathVariable Integer id) {
        return transactionService.findById(id);
    }

    @GetMapping("/inventory/{inventoryId}")
    public Flux<Transaction> getByInventoryId(@PathVariable Integer inventoryId) {
        return transactionService.findByInventoryId(inventoryId);
    }

    @GetMapping("/product/{productId}")
    public Flux<Transaction> getByProductId(@PathVariable Integer productId) {
        return transactionService.findByProductId(productId);
    }

    @GetMapping("/type/{type}")
    public Flux<Transaction> getByType(@PathVariable String type) {
        return transactionService.findByType(type);
    }

    @GetMapping("/active")
    public Flux<Transaction> listActive() {
        return transactionService.findActive();
    }

    @PostMapping
    public Mono<ResponseEntity<Transaction>> create(@RequestBody Transaction transaction) {
        return transactionService.save(transaction)
                .map(savedTransaction -> ResponseEntity.status(HttpStatus.CREATED).body(savedTransaction))
                .onErrorReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Transaction>> update(@PathVariable Integer id, @RequestBody Transaction transaction) {
        return transactionService.update(id, transaction)
                .map(updatedTransaction -> ResponseEntity.ok(updatedTransaction))
                .onErrorReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
    }

    @PutMapping("/{id}/deactivate")
    public Mono<Void> deactivate(@PathVariable Integer id) {
        return transactionService.delete(id);
    }

    @PutMapping("/{id}/restore")
    public Mono<Transaction> restore(@PathVariable Integer id) {
        return transactionService.restore(id);
    }
}
