package com.example.banking.account;

import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import com.example.banking.movement.MovementService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService accounts;
    private final MovementService movements;
    public AccountController(AccountService accounts, MovementService movements) {
        this.accounts = accounts;
        this.movements = movements;
    }
    @GetMapping
    public List<AccountService.AccountResponse> list(Principal principal) { return accounts.list(principal.getName()); }

    @GetMapping("/{accountId}")
    public AccountService.AccountResponse get(Principal principal, @PathVariable("accountId") Long accountId) {
        return accounts.get(principal.getName(), accountId);
    }

    @GetMapping("/{accountId}/movements")
    public List<MovementService.MovementResponse> movements(Principal principal, @PathVariable("accountId") Long accountId,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return movements.list(principal.getName(), accountId,
                from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC),
                to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
    }
}
