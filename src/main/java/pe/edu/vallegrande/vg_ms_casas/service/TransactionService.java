package pe.edu.vallegrande.vg_ms_casas.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.edu.vallegrande.vg_ms_casas.model.Transaction;
import pe.edu.vallegrande.vg_ms_casas.repository.TransactionRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
public class TransactionService {

    @Autowired
    private TransactionRepository transactionRepository;

    public Flux<Transaction> findAll() {
        return transactionRepository.findAllByOrderByDateDesc();
    }

    public Mono<Transaction> findById(Integer id) {
        return transactionRepository.findById(id);
    }

    public Flux<Transaction> findByInventoryId(Integer inventoryId) {
        return transactionRepository.findByInventoryIdOrderByDateDesc(inventoryId);
    }

    public Flux<Transaction> findByProductId(Integer productId) {
        return transactionRepository.findByProductIdOrderByDateDesc(productId);
    }

    public Flux<Transaction> findByType(String type) {
        return transactionRepository.findByTypeOrderByDateDesc(type);
    }

    public Flux<Transaction> findActive() {
        return transactionRepository.findByStatusOrderByDateDesc("A");
    }

    public Mono<Transaction> save(Transaction transaction) {
        // Validaciones
        if (transaction.getInventoryId() == null) {
            return Mono.error(new IllegalArgumentException("Inventory ID is required"));
        }
        if (transaction.getProductId() == null) {
            return Mono.error(new IllegalArgumentException("Product ID is required"));
        }
        if (transaction.getType() == null || transaction.getType().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Transaction type is required"));
        }
        if (transaction.getQuantity() == null || transaction.getQuantity() <= 0) {
            return Mono.error(new IllegalArgumentException("Quantity must be greater than 0"));
        }
        if (transaction.getPreviousStock() == null || transaction.getPreviousStock() < 0) {
            return Mono.error(new IllegalArgumentException("Previous stock must be non-negative"));
        }
        if (transaction.getNewStock() == null || transaction.getNewStock() < 0) {
            return Mono.error(new IllegalArgumentException("New stock must be non-negative"));
        }

        // Validar tipos de transacción permitidos
        if (!isValidTransactionType(transaction.getType())) {
            return Mono.error(new IllegalArgumentException("Invalid transaction type. Allowed: ENTRADA, SALIDA, AJUSTE, DAÑO"));
        }

        // Establecer valores por defecto
        transaction.setIdTransaction(null); // Auto-generated
        if (transaction.getDate() == null) {
            transaction.setDate(LocalDateTime.now());
        }
        if (transaction.getStatus() == null || transaction.getStatus().isEmpty()) {
            transaction.setStatus("A");
        }

        return transactionRepository.save(transaction)
                .onErrorMap(throwable -> {
                    return new RuntimeException("Error saving transaction: " + throwable.getMessage(), throwable);
                });
    }

    public Mono<Transaction> update(Integer id, Transaction transaction) {
        return transactionRepository.findById(id)
                .flatMap(existingTransaction -> {
                    // Solo permitir actualizar ciertos campos
                    if (transaction.getReason() != null) {
                        existingTransaction.setReason(transaction.getReason());
                    }
                    if (transaction.getStatus() != null && !transaction.getStatus().isEmpty()) {
                        existingTransaction.setStatus(transaction.getStatus());
                    }
                    if (transaction.getUserId() != null) {
                        existingTransaction.setUserId(transaction.getUserId());
                    }

                    return transactionRepository.save(existingTransaction);
                })
                .onErrorMap(throwable -> {
                    return new RuntimeException("Error updating transaction: " + throwable.getMessage(), throwable);
                });
    }

    public Mono<Void> delete(Integer id) {
        return transactionRepository.findById(id)
                .flatMap(existingTransaction -> {
                    existingTransaction.setStatus("I");
                    return transactionRepository.save(existingTransaction);
                }).then();
    }

    public Mono<Transaction> restore(Integer id) {
        return transactionRepository.findById(id)
                .flatMap(existingTransaction -> {
                    existingTransaction.setStatus("A");
                    return transactionRepository.save(existingTransaction);
                });
    }

    private boolean isValidTransactionType(String type) {
        return "ENTRADA".equals(type) ||
                "SALIDA".equals(type) ||
                "AJUSTE".equals(type) ||
                "DAÑO".equals(type);
    }
}
