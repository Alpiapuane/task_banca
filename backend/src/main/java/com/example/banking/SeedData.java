package com.example.banking;

import com.example.banking.account.*;
import com.example.banking.movement.*;
import com.example.banking.user.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SeedData {
    @Bean
    ApplicationRunner seedDemoData(UserRepository users, AccountRepository accounts, MovementRepository movements,
                                   PasswordEncoder encoder,
                                   @Value("${app.demo-data.enabled:true}") boolean enabled) {
        return args -> {
            if (!enabled || users.count() > 0) return;
            AppUser alice = users.save(new AppUser("alice", encoder.encode("Demo1234!")));
            AppUser bob = users.save(new AppUser("bob", encoder.encode("Demo1234!")));
            BankAccount checking = accounts.save(new BankAccount(alice, "DEMO-ALICE-001",
                    "Everyday account", new BigDecimal("2350.75"), "EUR"));
            BankAccount savings = accounts.save(new BankAccount(alice, "DEMO-ALICE-002",
                    "Savings", new BigDecimal("7800.00"), "EUR"));
            BankAccount bobAccount = accounts.save(new BankAccount(bob, "DEMO-BOB-001",
                    "Bob's account", new BigDecimal("1000.00"), "EUR"));
            Instant now = Instant.now();
            movements.save(new Movement(checking, MovementType.DEPOSIT, new BigDecimal("2500.00"),
                    "Opening demo balance", "seed-alice-checking", now.minus(30, ChronoUnit.DAYS)));
            movements.save(new Movement(checking, MovementType.WITHDRAWAL, new BigDecimal("149.25"),
                    "Card payment", "seed-alice-card", now.minus(2, ChronoUnit.DAYS)));
            movements.save(new Movement(savings, MovementType.DEPOSIT, new BigDecimal("7800.00"),
                    "Opening demo balance", "seed-alice-savings", now.minus(15, ChronoUnit.DAYS)));
            movements.save(new Movement(bobAccount, MovementType.DEPOSIT, new BigDecimal("1000.00"),
                    "Opening demo balance", "seed-bob-account", now.minus(10, ChronoUnit.DAYS)));
        };
    }
}
