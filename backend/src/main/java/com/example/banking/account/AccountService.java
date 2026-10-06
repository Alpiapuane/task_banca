package com.example.banking.account;

import com.example.banking.common.ApiException;
import com.example.banking.user.UserRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final UserRepository users;
    public AccountService(AccountRepository accounts, UserRepository users) {
        this.accounts = accounts;
        this.users = users;
    }
    public List<AccountResponse> list(String username) {
        Long ownerId = ownerId(username);
        return accounts.findAllByOwnerIdOrderById(ownerId).stream().map(AccountResponse::from).toList();
    }
    public AccountResponse get(String username, Long id) {
        return AccountResponse.from(ownedAccount(ownerId(username), id));
    }
    public BankAccount ownedAccount(Long ownerId, Long id) {
        return accounts.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> ApiException.notFound("Account not found"));
    }
    public Long ownerId(String username) {
        return users.findByUsername(username).orElseThrow(() -> ApiException.notFound("User not found")).getId();
    }
    public record AccountResponse(Long id, String iban, String label, java.math.BigDecimal balance, String currency) {
        static AccountResponse from(BankAccount account) {
            return new AccountResponse(account.getId(), account.getIban(), account.getLabel(), account.getBalance(), account.getCurrency());
        }
    }
}
