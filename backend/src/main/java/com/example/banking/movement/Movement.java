package com.example.banking.movement;

import com.example.banking.account.BankAccount;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "account_movements", indexes = @Index(columnList = "account_id, occurred_at"))
public class Movement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private BankAccount account;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 160)
    private String description;
    @Column(nullable = false, length = 36)
    private String transferReference;
    @Column(nullable = false)
    private Instant occurredAt;

    protected Movement() {}
    public Movement(BankAccount account, MovementType type, BigDecimal amount, String description,
                    String transferReference, Instant occurredAt) {
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.transferReference = transferReference;
        this.occurredAt = occurredAt;
    }
    public Long getId() { return id; }
    public BankAccount getAccount() { return account; }
    public MovementType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public String getTransferReference() { return transferReference; }
    public Instant getOccurredAt() { return occurredAt; }
}
