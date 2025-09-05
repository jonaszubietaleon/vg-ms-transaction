package pe.edu.vallegrande.vg_ms_casas.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.edu.vallegrande.vg_ms_casas.model.Inventory;
import pe.edu.vallegrande.vg_ms_casas.repository.InventoryRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class InventoryService {

    @Autowired
    private InventoryRepository inventoryRepository;

    public Flux<Inventory> findAll() {
        return inventoryRepository.findAll();
    }

    public Mono<Inventory> findById(Integer id) {
        return inventoryRepository.findById(id);
    }

    public Mono<Inventory> save(Inventory inventory) {
        inventory.setIdInventory(null);

        if (inventory.getProductId() == null) {
            return Mono.error(new IllegalArgumentException("Product ID is required"));
        }
        if (inventory.getInitialStock() == null || inventory.getInitialStock() < 0) {
            return Mono.error(new IllegalArgumentException("Initial stock must be a positive number"));
        }
        if (inventory.getCurrentStock() == null || inventory.getCurrentStock() < 0) {
            return Mono.error(new IllegalArgumentException("Current stock must be a positive number"));
        }

        if (inventory.getStatus() == null || inventory.getStatus().isEmpty()) {
            inventory.setStatus("A");
        }

        return inventoryRepository.save(inventory)
                .onErrorMap(throwable -> {
                    return new RuntimeException("Error saving inventory: " + throwable.getMessage(), throwable);
                });
    }

    public Mono<Inventory> update(Integer id, Inventory inventory) {
        return inventoryRepository.findById(id)
                .flatMap(existingInventory -> {
                    if (inventory.getProductId() != null) {
                        existingInventory.setProductId(inventory.getProductId());
                    }
                    if (inventory.getInitialStock() != null && inventory.getInitialStock() >= 0) {
                        existingInventory.setInitialStock(inventory.getInitialStock());
                    }
                    if (inventory.getCurrentStock() != null && inventory.getCurrentStock() >= 0) {
                        existingInventory.setCurrentStock(inventory.getCurrentStock());
                    }
                    if (inventory.getStatus() != null && !inventory.getStatus().isEmpty()) {
                        existingInventory.setStatus(inventory.getStatus());
                    }
                    return inventoryRepository.save(existingInventory);
                })
                .onErrorMap(throwable -> {
                    return new RuntimeException("Error updating inventory: " + throwable.getMessage(), throwable);
                });
    }

    public Mono<Void> delete(Integer id) {
        return inventoryRepository.findById(id)
                .flatMap(existingInventory -> {
                    existingInventory.setStatus("I");
                    return inventoryRepository.save(existingInventory);
                }).then();
    }

    public Mono<Inventory> restore(Integer id) {
        return inventoryRepository.findById(id)
                .flatMap(existingInventory -> {
                    existingInventory.setStatus("A");
                    return inventoryRepository.save(existingInventory);
                });
    }

    public Flux<Inventory> findActive() {
        return inventoryRepository.findByStatus("A");
    }

    public Flux<Inventory> findInactive() {
        return inventoryRepository.findByStatus("I");
    }
}
