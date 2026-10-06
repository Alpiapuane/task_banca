package com.example.banking.movement;

import com.example.banking.account.AccountService;
import com.example.banking.common.ApiException;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MovementService {
    private final MovementRepository movements;
    private final AccountService accounts;
    public MovementService(MovementRepository movements, AccountService accounts) {
        this.movements = movements;
        this.accounts = accounts;
    }
    public List<MovementResponse> list(String username, Long accountId, Instant from, Instant to) {
        Long ownerId = accounts.ownerId(username);
        accounts.ownedAccount(ownerId, accountId);
        if (from != null && to != null && !from.isBefore(to))
            throw ApiException.badRequest("The from date must be on or before the to date");
        List<Movement> result;
        if (from != null && to != null)
            result = movements.findAllByAccountIdAndAccountOwnerIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(accountId, ownerId, from, to);
        else if (from != null)
            result = movements.findAllByAccountIdAndAccountOwnerIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(accountId, ownerId, from);
        else if (to != null)
            result = movements.findAllByAccountIdAndAccountOwnerIdAndOccurredAtLessThanOrderByOccurredAtDesc(accountId, ownerId, to);
        else result = movements.findAllByAccountIdAndAccountOwnerIdOrderByOccurredAtDesc(accountId, ownerId);
        return result.stream().map(MovementResponse::from).toList();
    }
    public record MovementResponse(Long id, MovementType type, java.math.BigDecimal amount, String description,
                                   String transferReference, Instant occurredAt) {
        static MovementResponse from(Movement movement) {
            return new MovementResponse(movement.getId(), movement.getType(), movement.getAmount(),
                    movement.getDescription(), movement.getTransferReference(), movement.getOccurredAt());
        }
    }
}
