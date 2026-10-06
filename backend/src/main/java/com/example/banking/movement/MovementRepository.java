package com.example.banking.movement;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovementRepository extends JpaRepository<Movement, Long> {
    List<Movement> findAllByAccountIdAndAccountOwnerIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(
            Long accountId, Long ownerId, Instant from, Instant to);
    List<Movement> findAllByAccountIdAndAccountOwnerIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
            Long accountId, Long ownerId, Instant from);
    List<Movement> findAllByAccountIdAndAccountOwnerIdAndOccurredAtLessThanOrderByOccurredAtDesc(
            Long accountId, Long ownerId, Instant to);
    List<Movement> findAllByAccountIdAndAccountOwnerIdOrderByOccurredAtDesc(Long accountId, Long ownerId);
}
