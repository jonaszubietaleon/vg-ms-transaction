package pe.edu.vallegrande.vg_ms_casas.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import pe.edu.vallegrande.vg_ms_casas.model.Consumption;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface ConsumptionRepository extends ReactiveCrudRepository<Consumption, Integer> {

    @Query("""
           SELECT c.id_consumption, c.date, c.id_home, c.product_id, c.quantity, c.weight, 
                  c.price, c.salevalue, c.status, h.names
           FROM consumption c
           INNER JOIN home h ON c.id_home = h.id_home
           WHERE c.status = :status
           """)
    Flux<Consumption> findByStatusWithNames(String status);

    @Query("""
           SELECT c.id_consumption, c.date, c.id_home, c.product_id, c.quantity, c.weight,
                  c.price, c.salevalue, c.status, h.names
           FROM consumption c
           INNER JOIN home h ON c.id_home = h.id_home
           WHERE c.id_consumption = :id
           """)
    Mono<Consumption> findByIdWithNames(Integer id);

    @Query("UPDATE consumption SET status = 'I' WHERE id_consumption = :id")
    Mono<Void> inactivateConsumption(Integer id);

    @Query("UPDATE consumption SET status = 'A' WHERE id_consumption = :id")
    Mono<Void> restoreConsumption(Integer id);

    @Query("""
           UPDATE consumption 
           SET date = :date, id_home = :idHome, product_id = :productId,
               quantity = :quantity, weight = :weight, price = :price, salevalue = :saleValue
           WHERE id_consumption = :id
           """)
    Mono<Void> updateConsumption(Integer id, LocalDate date, Integer idHome, Long productId,
                                 Integer quantity, Double weight, Integer price, Double saleValue);

    @Query("""
           SELECT c.id_consumption, c.date, c.id_home, c.product_id, c.quantity, c.weight,
                  c.price, c.salevalue, c.status, h.names
           FROM consumption c
           INNER JOIN home h ON c.id_home = h.id_home
           WHERE c.date BETWEEN :startDate AND :endDate
           """)
    Flux<Consumption> findByDateRange(LocalDate startDate, LocalDate endDate);

    @Query("""
           SELECT c.id_consumption, c.date, c.id_home, c.product_id, c.quantity, c.weight,
                  c.price, c.salevalue, c.status, h.names
           FROM consumption c
           INNER JOIN home h ON c.id_home = h.id_home
           WHERE c.date BETWEEN :startDate AND :endDate
             AND c.status = :status
           """)
    Flux<Consumption> findByDateRangeAndStatus(LocalDate startDate, LocalDate endDate, String status);
}