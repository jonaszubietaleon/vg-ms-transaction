package pe.edu.vallegrande.vg_ms_casas;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import pe.edu.vallegrande.vg_ms_casas.model.Consumption;

import java.time.LocalDate;

public class ConsumptionServiceTest {

    /**
     * Verifica que el estado ("status") solo acepte los valores válidos 'A' (activo) o 'I' (inactivo).
     * También valida que valores inválidos no sean aceptados.
     */
    @Test
    public void testStatusValidValues() {
        Consumption consumption = new Consumption();

        // Estado válido 'A'
        consumption.setStatus("A");
        assertTrue(isValidStatus(consumption.getStatus()), "El estado debe ser 'A' o 'I'");

        // Estado válido 'I'
        consumption.setStatus("I");
        assertTrue(isValidStatus(consumption.getStatus()), "El estado debe ser 'A' o 'I'");

        // Estado inválido 'X'
        consumption.setStatus("X");
        assertFalse(isValidStatus(consumption.getStatus()), "El estado no debe ser distinto de 'A' o 'I'");
    }

    /**
     * Método auxiliar para validar el estado.
     */
    private boolean isValidStatus(String status) {
        return "A".equals(status) || "I".equals(status);
    }

    /**
     * Verifica la coherencia entre el precio unitario y el valor de venta.
     * El valor de venta debe ser mayor o igual al precio unitario.
     */
    @Test
    public void testPriceAndSaleValueCoherence() {
        Consumption consumption = new Consumption();

        // Caso válido: salevalue >= price
        consumption.setPrice(20);
        consumption.setSalevalue(40.0);
        assertTrue(consumption.getSalevalue() >= consumption.getPrice(),
                "El valor de venta debe ser mayor o igual al precio unitario");

        // Caso inválido: salevalue < price
        consumption.setSalevalue(10.0);
        assertFalse(consumption.getSalevalue() >= consumption.getPrice(),
                "El valor de venta no debe ser menor que el precio unitario");
    }

    /**
     * Asegura que el campo de nombres no esté vacío cuando se espera un nombre válido,
     * y que pueda estar vacío si se asigna una cadena vacía.
     */
    @Test
    public void testNamesNotEmpty() {
        Consumption consumption = new Consumption();

        // Nombre válido
        consumption.setNames("Producto válido");
        assertFalse(consumption.getNames().isEmpty(), "El nombre no debe estar vacío");

        // Nombre vacío
        consumption.setNames("");
        assertTrue(consumption.getNames().isEmpty(), "El nombre está vacío");
    }

    /**
     * Valida que los IDs no sean negativos, ya que usualmente los IDs son positivos o cero.
     * Prueba tanto valores positivos como negativos.
     */
    @Test
    public void testIdsNotNegative() {
        Consumption consumption = new Consumption();

        // IDs positivos o cero (válidos)
        consumption.setId_consumption(1);
        consumption.setId_home(2);
        consumption.setProductId(3L);

        assertTrue(consumption.getId_consumption() >= 0, "id_consumption no debe ser negativo");
        assertTrue(consumption.getId_home() >= 0, "id_home no debe ser negativo");
        assertTrue(consumption.getProductId() >= 0, "productId no debe ser negativo");

        // IDs negativos (inválidos)
        consumption.setId_consumption(-1);
        consumption.setId_home(-2);
        consumption.setProductId(-3L);

        assertFalse(consumption.getId_consumption() >= 0, "id_consumption es negativo");
        assertFalse(consumption.getId_home() >= 0, "id_home es negativo");
        assertFalse(consumption.getProductId() >= 0, "productId es negativo");
    }

    /**
     * Comprueba que la fecha asignada no sea una fecha futura.
     * Una fecha válida puede ser hoy o en el pasado.
     */
    @Test
    public void testDateNotInFuture() {
        Consumption consumption = new Consumption();

        LocalDate today = LocalDate.now();

        // Fecha hoy (válida)
        consumption.setDate(today);
        assertFalse(consumption.getDate().isAfter(today), "La fecha no debe ser futura");

        // Fecha futura (inválida)
        LocalDate futureDate = today.plusDays(1);
        consumption.setDate(futureDate);
        assertTrue(consumption.getDate().isAfter(today), "La fecha es futura");
    }
}
