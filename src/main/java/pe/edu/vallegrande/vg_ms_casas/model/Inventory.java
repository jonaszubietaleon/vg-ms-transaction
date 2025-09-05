package pe.edu.vallegrande.vg_ms_casas.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("inventory_consumption")
public class Inventory {

    @Id
    @Column("id_inventory")
    private Integer idInventory;

    @Column("product_id")
    private Integer productId;

    @Column("initial_stock")
    private Integer initialStock;

    @Column("current_stock")
    private Integer currentStock;

    private String status;
}
