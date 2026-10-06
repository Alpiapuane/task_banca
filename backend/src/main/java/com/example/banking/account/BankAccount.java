package com.example.banking.account;

import com.example.banking.user.AppUser;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "bank_accounts", uniqueConstraints = @UniqueConstraint(columnNames = "iban"))
public class BankAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;
    @Column(nullable = false, length = 34)
    private String iban;
    @Column(nullable = false, length = 80)
    private String label;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;
    @Column(nullable = false, length = 3)
    private String currency;

    protected BankAccount() {}
    public BankAccount(AppUser owner, String iban, String label, BigDecimal balance, String currency) {
        this.owner = owner;
        this.iban = iban;
        this.label = label;
        this.balance = balance;
        this.currency = currency;
    }
    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getIban() { return iban; }
    public String getLabel() { return label; }
    public BigDecimal getBalance() { return balance; }
    public String getCurrency() { return currency; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
