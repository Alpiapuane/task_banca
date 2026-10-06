package com.example.banking.movement;

import com.example.banking.account.AccountRepository;
import com.example.banking.account.BankAccount;
import com.example.banking.common.ApiException;
import com.example.banking.user.UserRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TransferService {
    private static final BigDecimal MAX_BALANCE = new BigDecimal("99999999999999999.99");
    private final AccountRepository accounts;
    private final MovementRepository movements;
    private final UserRepository users;
    public TransferService(AccountRepository accounts, MovementRepository movements, UserRepository users) {
        this.accounts = accounts;
        this.movements = movements;
        this.users = users;
    }

    @Transactional
    public TransferResponse transfer(String username, TransferRequest request) {
        if (request.sourceAccountId().equals(request.destinationAccountId()))
            throw ApiException.badRequest("Source and destination accounts must differ");
        Long ownerId = users.findByUsername(username).orElseThrow(() -> ApiException.notFound("User not found")).getId();
        Long firstId = Math.min(request.sourceAccountId(), request.destinationAccountId());
        Long secondId = Math.max(request.sourceAccountId(), request.destinationAccountId());
        BankAccount first = accounts.findByIdForUpdate(firstId).orElseThrow(() -> ApiException.notFound("Account not found"));
        BankAccount second = accounts.findByIdForUpdate(secondId).orElseThrow(() -> ApiException.notFound("Account not found"));
        BankAccount source = first.getId().equals(request.sourceAccountId()) ? first : second;
        BankAccount destination = first.getId().equals(request.destinationAccountId()) ? first : second;
        if (!source.getOwner().getId().equals(ownerId) || !destination.getOwner().getId().equals(ownerId))
            throw ApiException.notFound("Account not found");
        BigDecimal amount = request.amount();
        if (source.getBalance().compareTo(amount) < 0)
            throw ApiException.badRequest("Insufficient funds");
        if (!source.getCurrency().equals(destination.getCurrency()))
            throw ApiException.badRequest("Transfers between different currencies are not supported");
        if (destination.getBalance().add(amount).compareTo(MAX_BALANCE) > 0)
            throw ApiException.badRequest("Destination balance would exceed the supported maximum");

        source.setBalance(source.getBalance().subtract(amount));
        destination.setBalance(destination.getBalance().add(amount));
        String reference = UUID.randomUUID().toString();
        Instant now = Instant.now();
        movements.save(new Movement(source, MovementType.TRANSFER_OUT, amount,
                "Transfer to " + destination.getIban(), reference, now));
        movements.save(new Movement(destination, MovementType.TRANSFER_IN, amount,
                "Transfer from " + source.getIban(), reference, now));
        return new TransferResponse(reference, source.getId(), destination.getId(), amount, source.getCurrency(), now);
    }

    public record TransferRequest(@jakarta.validation.constraints.NotNull Long sourceAccountId,
                                  @jakarta.validation.constraints.NotNull Long destinationAccountId,
                                  @jakarta.validation.constraints.NotNull
                                  @jakarta.validation.constraints.DecimalMin(value = "0.00", inclusive = false)
                                  @jakarta.validation.constraints.Digits(integer = 17, fraction = 2) BigDecimal amount) {}
    public record TransferResponse(String reference, Long sourceAccountId, Long destinationAccountId,
                                   BigDecimal amount, String currency, Instant occurredAt) {}
}
