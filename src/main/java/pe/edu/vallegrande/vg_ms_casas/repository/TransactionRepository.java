package pe.edu.vallegrande.vg_ms_casas.repository;

import pe.edu.vallegrande.vg_ms_casas.model.Transaction;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface TransactionRepository extends ReactiveCrudRepository<Transaction, Integer> {
    
    Flux<Transaction> findByInventoryIdOrderByDateDesc(Integer inventoryId);
    
    Flux<Transaction> findByProductIdOrderByDateDesc(Integer productId);
    
    Flux<Transaction> findByTypeOrderByDateDesc(String type);
    
    Flux<Transaction> findByStatusOrderByDateDesc(String status);
    
    Flux<Transaction> findAllByOrderByDateDesc();

    Flux<Transaction> findByConsumptionIdOrderByDateDesc(Integer consumptionId);
}
