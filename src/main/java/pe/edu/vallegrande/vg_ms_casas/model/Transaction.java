package pe.edu.vallegrande.vg_ms_casas.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("transactions")
public class Transaction {

    @Id
    @Column("id_transaction")
    private Integer idTransaction;

    @Column("inventory_id")
    private Integer inventoryId;

    @Column("product_id")
    private Integer productId;

    @Column("type")
    private String type; // ENTRADA, SALIDA, AJUSTE, DAÑO

    @Column("quantity")
    private Integer quantity;

    @Column("previous_stock")
    private Integer previousStock;

    @Column("new_stock")
    private Integer newStock;

    @Column("reason")
    private String reason;

    @Column("date")
    private LocalDateTime date;

    @Column("user_id")
    private Integer userId;

    @Column("status")
    private String status;

    @Column("consumption_id")
    private Integer consumptionId;
}
