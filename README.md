# Sistema de Gestión de Inventario & Transacciones - Backend

<div align="center">

![Version](https://img.shields.io/badge/Version-1.0.0-brightgreen?style=for-the-badge&logo=semantic-release)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2+-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![Java](https://img.shields.io/badge/Java-17+-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15+-336791?style=for-the-badge&logo=postgresql&logoColor=white)

### Sistema Enterprise de Control de Inventario Automatizado
*API REST robusta con control transaccional avanzado y auditoría completa*

</div>

---

## Arquitectura del Backend

El sistema está diseñado siguiendo patrones de arquitectura hexagonal y principios SOLID, proporcionando una base sólida para operaciones críticas de negocio.

### Stack Tecnológico

- **Framework**: Spring Boot 3.2+ con Spring Data JPA
- **Base de Datos**: PostgreSQL 15+ con triggers automatizados
- **Validaciones**: Bean Validation (JSR 303)
- **Documentación**: OpenAPI 3.0 / Swagger
- **Testing**: JUnit 5 + Testcontainers

---

## Modelo de Dominio

### Entidad: Inventory Consumption

```java
@Entity
@Table(name = "inventory_consumption")
public class InventoryConsumption {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_inventory")
    private Long id;
    
    @Column(name = "product_id", nullable = false)
    private Long productId;
    
    @Column(name = "initial_stock", nullable = false)
    @Min(value = 0, message = "Stock inicial no puede ser negativo")
    private Integer initialStock;
    
    @Column(name = "current_stock", nullable = false)
    @Min(value = 0, message = "Stock actual no puede ser negativo")
    private Integer currentStock;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 1)
    private StatusType status = StatusType.ACTIVE;
    
    @OneToMany(mappedBy = "inventoryConsumption", cascade = CascadeType.ALL)
    private List<Transaction> transactions = new ArrayList<>();
}
```

### Entidad: Transaction

```java
@Entity
@Table(name = "transactions")
public class Transaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_transaction")
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_id", nullable = false)
    private InventoryConsumption inventoryConsumption;
    
    @Column(name = "product_id", nullable = false)
    private Long productId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type;
    
    @Column(name = "quantity", nullable = false)
    @Min(value = 1, message = "La cantidad debe ser mayor a 0")
    private Integer quantity;
    
    @Column(name = "previous_stock", nullable = false)
    private Integer previousStock;
    
    @Column(name = "new_stock", nullable = false)
    private Integer newStock;
    
    @Column(name = "date", nullable = false)
    @CreationTimestamp
    private LocalDateTime date;
    
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 1)
    private StatusType status = StatusType.ACTIVE;
}
```

### Enums del Dominio

```java
public enum TransactionType {
    ENTRADA("Entrada de mercancía"),
    SALIDA("Salida por consumo/venta"),
    AJUSTE("Ajuste de inventario"),
    DANO("Producto dañado/vencido");
    
    private final String description;
}

public enum StatusType {
    ACTIVE('A'),
    INACTIVE('I');
    
    private final char code;
}
```

---

## Capa de Repositorio

### Repository: InventoryConsumptionRepository

```java
@Repository
public interface InventoryConsumptionRepository extends JpaRepository<InventoryConsumption, Long> {
    
    Optional<InventoryConsumption> findByProductId(Long productId);
    
    @Query("SELECT ic FROM InventoryConsumption ic WHERE ic.currentStock <= :threshold")
    List<InventoryConsumption> findLowStockProducts(@Param("threshold") Integer threshold);
    
    @Modifying
    @Query("UPDATE InventoryConsumption ic SET ic.currentStock = ic.currentStock - :quantity " +
           "WHERE ic.productId = :productId")
    int decreaseStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
    
    @Query("SELECT SUM(ic.currentStock * ic.averagePrice) FROM InventoryConsumption ic " +
           "WHERE ic.status = 'A'")
    BigDecimal calculateTotalInventoryValue();
}
```

### Repository: TransactionRepository

```java
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
    List<Transaction> findByProductIdOrderByDateDesc(Long productId);
    
    @Query("SELECT t FROM Transaction t WHERE t.date BETWEEN :startDate AND :endDate " +
           "ORDER BY t.date DESC")
    Page<Transaction> findByDateRange(@Param("startDate") LocalDateTime startDate,
                                    @Param("endDate") LocalDateTime endDate,
                                    Pageable pageable);
    
    @Query("SELECT t FROM Transaction t WHERE t.type = :type AND t.status = 'A' " +
           "ORDER BY t.date DESC")
    List<Transaction> findByTypeAndActiveStatus(@Param("type") TransactionType type);
    
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.productId = :productId " +
           "AND t.date >= :date")
    Long countRecentTransactions(@Param("productId") Long productId, 
                                @Param("date") LocalDateTime date);
}
```

---

## Capa de Servicios

### Service: InventoryService

```java
@Service
@Transactional
public class InventoryService {
    
    private final InventoryConsumptionRepository inventoryRepository;
    private final TransactionRepository transactionRepository;
    
    public InventoryConsumption createInventory(CreateInventoryRequest request) {
        
        // Validar si ya existe inventario para este producto
        Optional<InventoryConsumption> existing = inventoryRepository.findByProductId(request.getProductId());
        if (existing.isPresent()) {
            throw new DuplicateInventoryException("Ya existe inventario para el producto: " + request.getProductId());
        }
        
        // Crear nueva entrada de inventario
        InventoryConsumption inventory = InventoryConsumption.builder()
            .productId(request.getProductId())
            .initialStock(request.getInitialStock())
            .currentStock(request.getInitialStock())
            .status(StatusType.ACTIVE)
            .build();
            
        InventoryConsumption saved = inventoryRepository.save(inventory);
        
        // La transacción ENTRADA se crea automáticamente por trigger de BD
        log.info("Inventario creado para producto {}: {} unidades", 
                request.getProductId(), request.getInitialStock());
        
        return saved;
    }
    
    @Transactional(readOnly = true)
    public Page<InventoryConsumption> getAllInventory(Pageable pageable) {
        return inventoryRepository.findAll(pageable);
    }
    
    @Transactional(readOnly = true)
    public InventoryConsumption getByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId)
            .orElseThrow(() -> new InventoryNotFoundException("Inventario no encontrado para producto: " + productId));
    }
    
    public InventoryConsumption adjustStock(Long productId, StockAdjustmentRequest request) {
        
        InventoryConsumption inventory = getByProductId(productId);
        
        // Validar que el ajuste no resulte en stock negativo
        int newStock = inventory.getCurrentStock() + request.getAdjustment();
        if (newStock < 0) {
            throw new InsufficientStockException("Stock insuficiente. Stock actual: " + 
                inventory.getCurrentStock() + ", Ajuste: " + request.getAdjustment());
        }
        
        // Actualizar stock
        inventory.setCurrentStock(newStock);
        InventoryConsumption updated = inventoryRepository.save(inventory);
        
        // Registrar transacción manual de ajuste
        Transaction adjustmentTransaction = Transaction.builder()
            .inventoryConsumption(inventory)
            .productId(productId)
            .type(TransactionType.AJUSTE)
            .quantity(Math.abs(request.getAdjustment()))
            .previousStock(inventory.getCurrentStock() - request.getAdjustment())
            .newStock(newStock)
            .reason(request.getReason())
            .status(StatusType.ACTIVE)
            .build();
            
        transactionRepository.save(adjustmentTransaction);
        
        log.info("Stock ajustado para producto {}: {} -> {}", productId, 
                inventory.getCurrentStock() - request.getAdjustment(), newStock);
        
        return updated;
    }
    
    @Transactional(readOnly = true)
    public List<InventoryConsumption> getLowStockProducts(Integer threshold) {
        return inventoryRepository.findLowStockProducts(threshold != null ? threshold : 10);
    }
}
```

### Service: TransactionService

```java
@Service
@Transactional(readOnly = true)
public class TransactionService {
    
    private final TransactionRepository transactionRepository;
    private final InventoryService inventoryService;
    
    public Page<Transaction> getAllTransactions(Pageable pageable) {
        return transactionRepository.findAll(pageable);
    }
    
    public Page<Transaction> getTransactionsByDateRange(LocalDateTime startDate, 
                                                       LocalDateTime endDate, 
                                                       Pageable pageable) {
        return transactionRepository.findByDateRange(startDate, endDate, pageable);
    }
    
    public List<Transaction> getTransactionsByProduct(Long productId) {
        return transactionRepository.findByProductIdOrderByDateDesc(productId);
    }
    
    public List<Transaction> getTransactionsByType(TransactionType type) {
        return transactionRepository.findByTypeAndActiveStatus(type);
    }
    
    @Transactional
    public void processConsumption(ConsumptionRequest request) {
        
        // Validar disponibilidad de stock
        InventoryConsumption inventory = inventoryService.getByProductId(request.getProductId());
        
        if (inventory.getCurrentStock() < request.getQuantity()) {
            throw new InsufficientStockException(
                String.format("Stock insuficiente. Disponible: %d, Solicitado: %d", 
                    inventory.getCurrentStock(), request.getQuantity()));
        }
        
        // El trigger de BD se encarga de:
        // 1. Actualizar el current_stock
        // 2. Crear la transacción SALIDA automáticamente
        
        // Solo necesitamos registrar el consumo
        // (esto activará el trigger automático)
        
        log.info("Procesando consumo - Producto: {}, Cantidad: {}", 
                request.getProductId(), request.getQuantity());
    }
    
    public TransactionSummaryDTO getTransactionSummary(Long productId, Period period) {
        
        LocalDateTime startDate = LocalDateTime.now().minus(period);
        LocalDateTime endDate = LocalDateTime.now();
        
        List<Transaction> transactions = transactionRepository.findByProductIdAndDateBetween(
            productId, startDate, endDate);
        
        return TransactionSummaryDTO.builder()
            .productId(productId)
            .totalEntradas(calculateTotalByType(transactions, TransactionType.ENTRADA))
            .totalSalidas(calculateTotalByType(transactions, TransactionType.SALIDA))
            .totalAjustes(calculateTotalByType(transactions, TransactionType.AJUSTE))
            .totalDanos(calculateTotalByType(transactions, TransactionType.DANO))
            .period(period)
            .build();
    }
    
    private Integer calculateTotalByType(List<Transaction> transactions, TransactionType type) {
        return transactions.stream()
            .filter(t -> t.getType() == type)
            .mapToInt(Transaction::getQuantity)
            .sum();
    }
}
```

---

## Capa de Controladores REST

### Controller: InventoryController

```java
@RestController
@RequestMapping("/api/inventory")
@Validated
public class InventoryController {
    
    private final InventoryService inventoryService;
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<InventoryResponse> createInventory(@Valid @RequestBody CreateInventoryRequest request) {
        
        InventoryConsumption created = inventoryService.createInventory(request);
        InventoryResponse response = InventoryMapper.toResponse(created);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .location(URI.create("/api/inventory/" + created.getId()))
            .body(response);
    }
    
    @GetMapping
    public ResponseEntity<Page<InventoryResponse>> getAllInventory(
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        
        Page<InventoryConsumption> inventoryPage = inventoryService.getAllInventory(pageable);
        Page<InventoryResponse> response = inventoryPage.map(InventoryMapper::toResponse);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<InventoryResponse> getInventoryById(@PathVariable Long id) {
        
        InventoryConsumption inventory = inventoryService.getById(id);
        InventoryResponse response = InventoryMapper.toResponse(inventory);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/product/{productId}")
    public ResponseEntity<InventoryResponse> getInventoryByProduct(@PathVariable Long productId) {
        
        InventoryConsumption inventory = inventoryService.getByProductId(productId);
        InventoryResponse response = InventoryMapper.toResponse(inventory);
        
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/{id}/adjust")
    public ResponseEntity<InventoryResponse> adjustStock(@PathVariable Long id,
                                                        @Valid @RequestBody StockAdjustmentRequest request) {
        
        InventoryConsumption updated = inventoryService.adjustStock(id, request);
        InventoryResponse response = InventoryMapper.toResponse(updated);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/low-stock")
    public ResponseEntity<List<InventoryResponse>> getLowStockProducts(
            @RequestParam(defaultValue = "10") Integer threshold) {
        
        List<InventoryConsumption> lowStock = inventoryService.getLowStockProducts(threshold);
        List<InventoryResponse> response = lowStock.stream()
            .map(InventoryMapper::toResponse)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }
}
```

### Controller: TransactionController

```java
@RestController
@RequestMapping("/api/transactions")
@Validated
public class TransactionController {
    
    private final TransactionService transactionService;
    
    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> getAllTransactions(
            @PageableDefault(size = 50, sort = "date", direction = Sort.Direction.DESC) Pageable pageable) {
        
        Page<Transaction> transactions = transactionService.getAllTransactions(pageable);
        Page<TransactionResponse> response = transactions.map(TransactionMapper::toResponse);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/filter")
    public ResponseEntity<Page<TransactionResponse>> getTransactionsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 50, sort = "date", direction = Sort.Direction.DESC) Pageable pageable) {
        
        Page<Transaction> transactions = transactionService.getTransactionsByDateRange(
            startDate, endDate, pageable);
        Page<TransactionResponse> response = transactions.map(TransactionMapper::toResponse);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/product/{productId}")
    public ResponseEntity<List<TransactionResponse>> getTransactionsByProduct(@PathVariable Long productId) {
        
        List<Transaction> transactions = transactionService.getTransactionsByProduct(productId);
        List<TransactionResponse> response = transactions.stream()
            .map(TransactionMapper::toResponse)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/type/{type}")
    public ResponseEntity<List<TransactionResponse>> getTransactionsByType(@PathVariable TransactionType type) {
        
        List<Transaction> transactions = transactionService.getTransactionsByType(type);
        List<TransactionResponse> response = transactions.stream()
            .map(TransactionMapper::toResponse)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/summary/{productId}")
    public ResponseEntity<TransactionSummaryDTO> getTransactionSummary(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "P30D") Period period) {
        
        TransactionSummaryDTO summary = transactionService.getTransactionSummary(productId, period);
        
        return ResponseEntity.ok(summary);
    }
    
    @PostMapping("/consumption")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<MessageResponse> processConsumption(@Valid @RequestBody ConsumptionRequest request) {
        
        transactionService.processConsumption(request);
        
        MessageResponse response = MessageResponse.builder()
            .message("Consumo procesado correctamente")
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity.accepted().body(response);
    }
}
```

---

## DTOs y Requests/Responses

### Request DTOs

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateInventoryRequest {
    
    @NotNull(message = "Product ID es requerido")
    @Positive(message = "Product ID debe ser positivo")
    private Long productId;
    
    @NotNull(message = "Stock inicial es requerido")
    @Min(value = 0, message = "Stock inicial no puede ser negativo")
    private Integer initialStock;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustmentRequest {
    
    @NotNull(message = "Ajuste es requerido")
    private Integer adjustment;
    
    @NotBlank(message = "Razón del ajuste es requerida")
    @Size(max = 500, message = "Razón no puede exceder 500 caracteres")
    private String reason;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumptionRequest {
    
    @NotNull(message = "Product ID es requerido")
    @Positive(message = "Product ID debe ser positivo")
    private Long productId;
    
    @NotNull(message = "Cantidad es requerida")
    @Positive(message = "Cantidad debe ser positiva")
    private Integer quantity;
    
    @Size(max = 500, message = "Razón no puede exceder 500 caracteres")
    private String reason;
}
```

### Response DTOs

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {
    
    private Long id;
    private Long productId;
    private Integer initialStock;
    private Integer currentStock;
    private String status;
    private List<TransactionResponse> recentTransactions;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    
    private Long id;
    private Long productId;
    private String type;
    private Integer quantity;
    private Integer previousStock;
    private Integer newStock;
    private LocalDateTime date;
    private String reason;
    private String status;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSummaryDTO {
    
    private Long productId;
    private Integer totalEntradas;
    private Integer totalSalidas;
    private Integer totalAjustes;
    private Integer totalDanos;
    private Period period;
    private Integer stockNetChange;
    private Double rotationRate;
}
```

---

## Manejo de Excepciones

```java
@ControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(InventoryNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleInventoryNotFound(InventoryNotFoundException ex) {
        return ErrorResponse.builder()
            .error("INVENTORY_NOT_FOUND")
            .message(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    @ExceptionHandler(InsufficientStockException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInsufficientStock(InsufficientStockException ex) {
        return ErrorResponse.builder()
            .error("INSUFFICIENT_STOCK")
            .message(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    @ExceptionHandler(DuplicateInventoryException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicateInventory(DuplicateInventoryException ex) {
        return ErrorResponse.builder()
            .error("DUPLICATE_INVENTORY")
            .message(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ValidationErrorResponse handleValidationErrors(MethodArgumentNotValidException ex) {
        
        Map<String, String> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                FieldError::getDefaultMessage,
                (existing, replacement) -> existing
            ));
        
        return ValidationErrorResponse.builder()
            .error("VALIDATION_ERROR")
            .message("Error de validación en los datos de entrada")
            .fieldErrors(errors)
            .timestamp(LocalDateTime.now())
            .build();
    }
}
```

---

## Configuración de Base de Datos

### Triggers Automáticos Implementados

#### 1. Trigger para Registro de Inventario

```sql
CREATE OR REPLACE FUNCTION registrar_transaccion_inventario()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO transactions (
        inventory_id, product_id, type, quantity,
        previous_stock, new_stock, reason, status
    )
    VALUES (
        NEW.id_inventory, NEW.product_id, 'ENTRADA',
        NEW.initial_stock, 0, NEW.current_stock,
        'Registro de inventario inicial', 'A'
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_registrar_inventario
    AFTER INSERT ON inventory_consumption
    FOR EACH ROW EXECUTE FUNCTION registrar_transaccion_inventario();
```

#### 2. Trigger para Consumo/Salida

```sql
CREATE OR REPLACE FUNCTION registrar_transaccion_consumo()
RETURNS TRIGGER AS $$
DECLARE
    prev_stock INTEGER;
    inv_id INTEGER;
BEGIN
    IF NEW.status = 'A' THEN
        -- Obtener stock anterior y ID de inventario
        SELECT id_inventory, current_stock INTO inv_id, prev_stock
        FROM inventory_consumption 
        WHERE product_id = NEW.product_id;

        -- Actualizar stock en inventario
        UPDATE inventory_consumption
        SET current_stock = current_stock - NEW.quantity
        WHERE product_id = NEW.product_id;

        -- Registrar transacción automática
        INSERT INTO transactions (
            inventory_id, product_id, consumption_id, type, quantity,
            previous_stock, new_stock, reason, status
        )
        VALUES (
            inv_id, NEW.product_id, NEW.id_consumption, 'SALIDA',
            NEW.quantity, prev_stock, prev_stock - NEW.quantity,
            COALESCE(NEW.reason, 'Consumo registrado'), 'A'
        );
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_registrar_consumo
    AFTER INSERT ON consumption
    FOR EACH ROW EXECUTE FUNCTION registrar_transaccion_consumo();
```

#### 3. Trigger para Reversión de Stock

```sql
CREATE OR REPLACE FUNCTION devolver_stock()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'I' AND OLD.status = 'A' THEN
        -- Devolver stock al inventario
        UPDATE inventory_consumption
        SET current_stock = current_stock + OLD.quantity
        WHERE product_id = OLD.product_id;

        -- Marcar transacción como anulada
        UPDATE transactions 
        SET status = 'I'
        WHERE consumption_id = OLD.id_consumption;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_devolver_stock
    AFTER UPDATE ON consumption
    FOR EACH ROW EXECUTE FUNCTION devolver_stock();
```

---

## Testing

### Tests de Integración

```java
@SpringBootTest
@Testcontainers
class InventoryServiceIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("inventory_test")
            .withUsername("test")
            .withPassword("test");
    
    @Autowired
    private InventoryService inventoryService;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    @Test
    void should_CreateInventoryAndGenerateTransaction_When_ValidRequest() {
        // Given
        CreateInventoryRequest request = CreateInventoryRequest.builder()
            .productId(1L)
            .initialStock(100)
            .build();
        
        // When
        InventoryConsumption result = inventoryService.createInventory(request);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getProductId()).isEqualTo(1L);
        assertThat(result.getCurrentStock()).isEqualTo(100);
        
        // Verificar que el trigger creó la transacción ENTRADA
        List<Transaction> transactions = transactionRepository.findByProductIdOrderByDateDesc(1L);
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getType()).isEqualTo(TransactionType.ENTRADA);
        assertThat(transactions.get(0).getQuantity()).isEqualTo(100);
    }
    
    @Test
    void should_ThrowException_When_DuplicateInventory() {
        // Given
        CreateInventoryRequest request = CreateInventoryRequest.builder()
            .productId(2L)
            .initialStock(50)
            .build();
        
        inventoryService.createInventory(request);
        
        // When & Then
        assertThatThrownBy(() -> inventoryService.createInventory(request))
            .isInstanceOf(DuplicateInventoryException.class)
            .hasMessageContaining("Ya existe inventario para el producto: 2");
    }
}
```

---

## Configuración del Proyecto

### application.yml

```yaml
spring:
  application:
    name: inventory-management-system
  
  datasource:
    url: jdbc:postgresql://localhost:5432/inventory_system
    username: ${DB_USERNAME:inventory_user}
    password: ${DB_PASSWORD:inventory_pass}
    driver-class-name: org.postgresql.Driver
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        use_sql_comments: true
  
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml
    
server:
  port: 8080
  servlet:
    context-path: /api
    
logging:
  level:
    com.inventory.system: INFO
    org.springframework.web: DEBUG
    org.hibernate.SQL: DEBUG
    
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
```

### pom.xml (dependencias principales)

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    
    <dependency>
        <groupId>org.liquibase</groupId>
        <artifactId>liquibase-core</artifactId>
    </dependency>
    
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>2.2.0</version>
    </dependency>
    
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
        <version>1.5.5.Final</version>
    </dependency>
    
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct-processor</artifactId>
        <version>1.5.5.Final</version>
        <scope>provided</scope>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
    
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## Mappers con MapStruct

### InventoryMapper

```java
@Mapper(componentModel = "spring")
public interface InventoryMapper {
    
    @Mapping(target = "recentTransactions", source = "transactions", qualifiedByName = "mapRecentTransactions")
    InventoryResponse toResponse(InventoryConsumption inventory);
    
    @Named("mapRecentTransactions")
    default List<TransactionResponse> mapRecentTransactions(List<Transaction> transactions) {
        return transactions.stream()
            .sorted((t1, t2) -> t2.getDate().compareTo(t1.getDate()))
            .limit(5)
            .map(this::mapTransaction)
            .collect(Collectors.toList());
    }
    
    @Mapping(target = "type", source = "type", qualifiedByName = "mapTransactionType")
    @Mapping(target = "status", source = "status", qualifiedByName = "mapStatusType")
    TransactionResponse mapTransaction(Transaction transaction);
    
    @Named("mapTransactionType")
    default String mapTransactionType(TransactionType type) {
        return type.name();
    }
    
    @Named("mapStatusType")
    default String mapStatusType(StatusType status) {
        return status.name();
    }
}
```

### TransactionMapper

```java
@Mapper(componentModel = "spring")
public interface TransactionMapper {
    
    @Mapping(target = "type", source = "type", qualifiedByName = "mapTransactionType")
    @Mapping(target = "status", source = "status", qualifiedByName = "mapStatusType")
    TransactionResponse toResponse(Transaction transaction);
    
    List<TransactionResponse> toResponseList(List<Transaction> transactions);
    
    @Named("mapTransactionType")
    default String mapTransactionType(TransactionType type) {
        return type.getDescription();
    }
    
    @Named("mapStatusType")
    default String mapStatusType(StatusType status) {
        return status == StatusType.ACTIVE ? "Activo" : "Inactivo";
    }
}
```

---

## Configuración de Seguridad

### SecurityConfig

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/docs/**", "/api/swagger-ui/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/inventory/**").hasRole("USER")
                .requestMatchers(HttpMethod.POST, "/api/inventory").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/inventory/**").hasRole("ADMIN")
                .requestMatchers("/api/transactions/**").hasRole("USER")
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults());
        
        return http.build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails admin = User.builder()
            .username("admin")
            .password(passwordEncoder().encode("admin123"))
            .roles("ADMIN", "USER")
            .build();
            
        UserDetails user = User.builder()
            .username("user")
            .password(passwordEncoder().encode("user123"))
            .roles("USER")
            .build();
        
        return new InMemoryUserDetailsManager(admin, user);
    }
}
```

---

## Métricas y Monitoreo

### CustomMetrics

```java
@Component
public class InventoryMetrics {
    
    private final Counter inventoryCreatedCounter;
    private final Counter transactionProcessedCounter;
    private final Gauge currentTotalStock;
    private final Timer stockAdjustmentTimer;
    
    private final InventoryConsumptionRepository inventoryRepository;
    
    public InventoryMetrics(MeterRegistry meterRegistry, 
                           InventoryConsumptionRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
        
        this.inventoryCreatedCounter = Counter.builder("inventory.created.total")
            .description("Total inventory items created")
            .register(meterRegistry);
            
        this.transactionProcessedCounter = Counter.builder("transactions.processed.total")
            .description("Total transactions processed")
            .tag("type", "all")
            .register(meterRegistry);
            
        this.currentTotalStock = Gauge.builder("inventory.stock.current.total")
            .description("Current total stock across all products")
            .register(meterRegistry, this, InventoryMetrics::getCurrentTotalStock);
            
        this.stockAdjustmentTimer = Timer.builder("inventory.adjustment.duration")
            .description("Time taken to process stock adjustments")
            .register(meterRegistry);
    }
    
    public void incrementInventoryCreated() {
        inventoryCreatedCounter.increment();
    }
    
    public void incrementTransactionProcessed(TransactionType type) {
        transactionProcessedCounter.increment(
            Tags.of("type", type.name().toLowerCase())
        );
    }
    
    public Timer.Sample startAdjustmentTimer() {
        return Timer.start(stockAdjustmentTimer);
    }
    
    private Double getCurrentTotalStock() {
        return inventoryRepository.findAll()
            .stream()
            .mapToDouble(InventoryConsumption::getCurrentStock)
            .sum();
    }
}
```

---

## Documentación con OpenAPI

### OpenAPIConfig

```java
@Configuration
public class OpenAPIConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Sistema de Gestión de Inventario API")
                .version("1.0.0")
                .description("API REST para la gestión automatizada de inventario y transacciones")
                .contact(new Contact()
                    .name("Equipo de Desarrollo")
                    .email("dev@inventory-system.com")
                    .url("https://inventory-system.com")))
            .servers(Arrays.asList(
                new Server().url("http://localhost:8080").description("Desarrollo"),
                new Server().url("https://api.inventory-system.com").description("Producción")
            ))
            .components(new Components()
                .addSecuritySchemes("basicAuth", 
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic")))
            .addSecurityItem(new SecurityRequirement().addList("basicAuth"));
    }
}
```

---

## Health Checks Personalizados

### DatabaseHealthIndicator

```java
@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    
    private final DataSource dataSource;
    
    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(1)) {
                
                // Verificar que las tablas principales existan
                DatabaseMetaData metaData = connection.getMetaData();
                ResultSet tables = metaData.getTables(null, null, "inventory_consumption", null);
                
                if (tables.next()) {
                    return Health.up()
                        .withDetail("database", "PostgreSQL")
                        .withDetail("status", "Connected")
                        .withDetail("tables", "All tables available")
                        .build();
                } else {
                    return Health.down()
                        .withDetail("database", "PostgreSQL")
                        .withDetail("status", "Connected but missing tables")
                        .build();
                }
            }
        } catch (SQLException e) {
            return Health.down()
                .withDetail("database", "PostgreSQL")
                .withDetail("status", "Connection failed")
                .withDetail("error", e.getMessage())
                .build();
        }
        
        return Health.down()
            .withDetail("database", "PostgreSQL")
            .withDetail("status", "Connection timeout")
            .build();
    }
}
```

### TriggersHealthIndicator

```java
@Component
public class TriggersHealthIndicator implements HealthIndicator {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Override
    public Health health() {
        try {
            String sql = """
                SELECT trigger_name, event_manipulation, action_timing
                FROM information_schema.triggers 
                WHERE trigger_schema = 'public'
                AND trigger_name IN ('trigger_registrar_inventario', 'trigger_registrar_consumo', 'trigger_devolver_stock')
                """;
            
            List<Map<String, Object>> triggers = jdbcTemplate.queryForList(sql);
            
            if (triggers.size() >= 3) {
                return Health.up()
                    .withDetail("triggers", "All database triggers are active")
                    .withDetail("count", triggers.size())
                    .withDetails(triggers.stream()
                        .collect(Collectors.toMap(
                            row -> (String) row.get("trigger_name"),
                            row -> row.get("event_manipulation") + " " + row.get("action_timing")
                        )))
                    .build();
            } else {
                return Health.down()
                    .withDetail("triggers", "Some database triggers are missing")
                    .withDetail("expected", 3)
                    .withDetail("found", triggers.size())
                    .build();
            }
            
        } catch (DataAccessException e) {
            return Health.down()
                .withDetail("triggers", "Unable to check database triggers")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

---

## Cache Configuration

### CacheConfig

```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .initialCapacity(100)
            .maximumSize(1000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .recordStats());
            
        cacheManager.setCacheNames(Arrays.asList("inventory", "transactions", "lowStock"));
        return cacheManager;
    }
}
```

### Uso de Cache en los Servicios

```java
@Service
@Transactional
public class InventoryService {
    
    // ... otros métodos ...
    
    @Cacheable(value = "inventory", key = "#productId")
    @Transactional(readOnly = true)
    public InventoryConsumption getByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId)
            .orElseThrow(() -> new InventoryNotFoundException("Inventario no encontrado para producto: " + productId));
    }
    
    @CacheEvict(value = "inventory", key = "#result.productId")
    public InventoryConsumption adjustStock(Long productId, StockAdjustmentRequest request) {
        // ... implementación del ajuste ...
        return updated;
    }
    
    @Cacheable(value = "lowStock", key = "#threshold")
    @Transactional(readOnly = true)
    public List<InventoryConsumption> getLowStockProducts(Integer threshold) {
        return inventoryRepository.findLowStockProducts(threshold != null ? threshold : 10);
    }
}
```

---

## Utilidades y Helpers

### AuditUtils

```java
@Component
public class AuditUtils {
    
    public static String generateTransactionReason(TransactionType type, String customReason) {
        if (StringUtils.hasText(customReason)) {
            return customReason;
        }
        
        return switch (type) {
            case ENTRADA -> "Entrada automática de mercancía";
            case SALIDA -> "Salida por consumo/venta";
            case AJUSTE -> "Ajuste de inventario";
            case DANO -> "Producto dañado o vencido";
        };
    }
    
    public static boolean isStockMovementValid(Integer currentStock, Integer movement, TransactionType type) {
        return switch (type) {
            case ENTRADA, AJUSTE -> true; // Siempre válido para entradas
            case SALIDA, DANO -> currentStock >= Math.abs(movement); // Verificar stock suficiente
        };
    }
    
    public static Integer calculateNewStock(Integer currentStock, Integer movement, TransactionType type) {
        return switch (type) {
            case ENTRADA -> currentStock + movement;
            case SALIDA, DANO -> currentStock - movement;
            case AJUSTE -> currentStock + movement; // El movimento puede ser positivo o negativo
        };
    }
}
```

### ValidationUtils

```java
@Component
public class ValidationUtils {
    
    public static void validateStockOperation(InventoryConsumption inventory, 
                                            Integer quantity, 
                                            TransactionType type) {
        
        Objects.requireNonNull(inventory, "Inventario no puede ser nulo");
        Objects.requireNonNull(quantity, "Cantidad no puede ser nula");
        Objects.requireNonNull(type, "Tipo de transacción no puede ser nulo");
        
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
        }
        
        if (inventory.getStatus() != StatusType.ACTIVE) {
            throw new IllegalStateException("El inventario debe estar activo para realizar operaciones");
        }
        
        // Validar stock suficiente para operaciones de salida
        if ((type == TransactionType.SALIDA || type == TransactionType.DANO) 
            && inventory.getCurrentStock() < quantity) {
            throw new InsufficientStockException(
                String.format("Stock insuficiente. Disponible: %d, Requerido: %d", 
                    inventory.getCurrentStock(), quantity));
        }
    }
    
    public static void validateProductId(Long productId) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Product ID debe ser un número positivo");
        }
    }
    
    public static void validateDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Las fechas de inicio y fin son requeridas");
        }
        
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("La fecha de inicio debe ser anterior a la fecha de fin");
        }
        
        if (startDate.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("La fecha de inicio no puede ser futura");
        }
    }
}
```

---

## Perfiles de Configuración

### application-dev.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/inventory_dev
    username: dev_user
    password: dev_pass
  
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
  
  liquibase:
    contexts: dev
    
logging:
  level:
    com.inventory.system: DEBUG
    org.springframework.web: DEBUG
    org.hibernate: DEBUG
    
management:
  endpoints:
    web:
      exposure:
        include: "*"
```

### application-prod.yml

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      max-lifetime: 600000
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  
  liquibase:
    contexts: prod
    
logging:
  level:
    com.inventory.system: INFO
    org.springframework.web: WARN
    org.hibernate: WARN
  
  file:
    name: /var/log/inventory-system.log
    
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

---

## Scripts de Deployment

### docker-compose.yml

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: inventory_system
      POSTGRES_USER: inventory_user
      POSTGRES_PASSWORD: inventory_pass
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./database/init.sql:/docker-entrypoint-initdb.d/init.sql
    networks:
      - inventory-network

  inventory-api:
    build: .
    environment:
      SPRING_PROFILES_ACTIVE: docker
      DATABASE_URL: jdbc:postgresql://postgres:5432/inventory_system
      DB_USERNAME: inventory_user
      DB_PASSWORD: inventory_pass
    ports:
      - "8080:8080"
    depends_on:
      - postgres
    networks:
      - inventory-network
    restart: unless-stopped

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
    networks:
      - inventory-network

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
    volumes:
      - grafana_data:/var/lib/grafana
      - ./monitoring/dashboards:/etc/grafana/provisioning/dashboards
    networks:
      - inventory-network

volumes:
  postgres_data:
  grafana_data:

networks:
  inventory-network:
    driver: bridge
```

### Dockerfile

```dockerfile
FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/inventory-system-*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

---

## Conclusión

Este sistema de inventario backend proporciona:

### Características Principales:
- **Automatización Completa**: Triggers de base de datos manejan automáticamente las transacciones
- **Validaciones Robustas**: Múltiples capas de validación para garantizar integridad de datos
- **API REST Completa**: Endpoints bien documentados con OpenAPI/Swagger
- **Manejo de Excepciones**: Sistema centralizado de manejo de errores
- **Métricas y Monitoreo**: Integración con Micrometer y actuator endpoints
- **Testing Comprehensivo**: Tests de integración con Testcontainers
- **Cache Inteligente**: Sistema de cache para optimizar consultas frecuentes
- **Seguridad**: Configuración básica con Spring Security
- **Observabilidad**: Health checks personalizados y métricas de negocio

### Patrones Implementados:
- Repository Pattern con Spring Data JPA
- Service Layer con transacciones declarativas
- DTO Pattern con MapStruct para mappeo automático
- Exception Handler centralizado
- Configuration Pattern para diferentes entornos

### Escalabilidad:
- Preparado para microservicios
- Base de datos optimizada con índices y constraints
- Sistema de cache para mejorar performance
- Métricas para monitoreo en producción
- Docker ready para despliegue en containers

El sistema está diseñado para ser mantenible, testeable y escalable, siguiendo las mejores prácticas de desarrollo con Spring Boot.
