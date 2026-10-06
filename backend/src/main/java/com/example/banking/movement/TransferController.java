package com.example.banking.movement;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {
    private final TransferService transfers;
    public TransferController(TransferService transfers) { this.transfers = transfers; }
    @PostMapping
    public TransferService.TransferResponse transfer(Principal principal,
            @Valid @RequestBody TransferService.TransferRequest request) {
        return transfers.transfer(principal.getName(), request);
    }
}
