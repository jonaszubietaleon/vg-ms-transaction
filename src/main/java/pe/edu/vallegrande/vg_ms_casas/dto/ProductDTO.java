package pe.edu.vallegrande.vg_ms_casas.dto;

public class ProductDTO {
    private Long productId;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    @Override
    public String toString() {
        return "ProductDTO{" +
                "productId=" + productId +
                '}';
    }
}
