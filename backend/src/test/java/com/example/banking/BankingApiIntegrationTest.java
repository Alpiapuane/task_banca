package com.example.banking;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.banking.account.AccountRepository;
import com.example.banking.account.BankAccount;
import com.example.banking.movement.Movement;
import com.example.banking.movement.MovementRepository;
import com.example.banking.movement.MovementType;
import com.example.banking.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BankingApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountRepository accounts;
    @SpyBean MovementRepository movements;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void loginReturnsBearerTokenAndRejectsWrongPassword() throws Exception {
        String storedHash = users.findByUsername("alice").orElseThrow().getPasswordHash();
        assertTrue(storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$"));
        assertTrue(passwordEncoder.matches("Demo1234!", storedHash));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"Demo1234!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginValidatesRequiredFieldsAndDoesNotExposePasswordHash() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"Demo1234!\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isNotEmpty());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/accounts").header("Authorization", bearer(token("alice"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].owner").doesNotExist());
    }

    @Test
    void requiresAuthenticationAndScopesAccountsToOwner() throws Exception {
        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":1,\"destinationAccountId\":2,\"amount\":1.00}"))
                .andExpect(status().isUnauthorized());
        String alice = token("alice");
        String bob = token("bob");
        mvc.perform(get("/api/accounts").header("Authorization", bearer(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].iban").value("DEMO-ALICE-001"))
                .andExpect(jsonPath("$[1].iban").value("DEMO-ALICE-002"));
        Long bobId = users.findByUsername("bob").orElseThrow().getId();
        Long bobAccountId = accounts.findAllByOwnerIdOrderById(bobId).get(0).getId();
        Long aliceAccountId = accounts.findAllByOwnerIdOrderById(users.findByUsername("alice").orElseThrow().getId())
                .get(0).getId();
        mvc.perform(get("/api/accounts/" + aliceAccountId).header("Authorization", bearer(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(aliceAccountId))
                .andExpect(jsonPath("$.iban").value("DEMO-ALICE-001"));
        mvc.perform(get("/api/accounts/" + bobAccountId).header("Authorization", bearer(alice)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/accounts/" + bobAccountId + "/movements").header("Authorization", bearer(alice)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/accounts/999999/movements").header("Authorization", bearer(alice)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/accounts").header("Authorization", bearer(bob)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void transferIsExactAndCreatesPairedMovementsAtomically() throws Exception {
        String token = token("alice");
        var initial = accounts.findAllByOwnerIdOrderById(users.findByUsername("alice").orElseThrow().getId());
        BigDecimal sourceBefore = initial.get(0).getBalance();
        BigDecimal destinationBefore = initial.get(1).getBalance();
        long movementsBefore = movements.count();
        MvcResult transferResult = mvc.perform(post("/api/transfers").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":" + initial.get(0).getId() + ",\"destinationAccountId\":"
                                + initial.get(1).getId() + ",\"amount\":12.34}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(12.34))
                .andExpect(jsonPath("$.reference").isNotEmpty()).andReturn();
        assertEquals(0, accounts.findById(initial.get(0).getId()).orElseThrow().getBalance()
                .compareTo(sourceBefore.subtract(new BigDecimal("12.34"))));
        assertEquals(0, accounts.findById(initial.get(1).getId()).orElseThrow().getBalance()
                .compareTo(destinationBefore.add(new BigDecimal("12.34"))));
        String reference = mapper.readTree(transferResult.getResponse().getContentAsString()).get("reference").asText();
        assertEquals(2, movements.count() - movementsBefore);
        assertEquals(2, movements.findAll().stream().filter(m -> reference.equals(m.getTransferReference())).count());
        mvc.perform(get("/api/accounts/" + initial.get(0).getId() + "/movements")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$[0].transferReference").value(reference));
        mvc.perform(get("/api/accounts/" + initial.get(1).getId() + "/movements")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("TRANSFER_IN"))
                .andExpect(jsonPath("$[0].transferReference").value(reference));
    }

    @Test
    void rejectsMalformedTransferPayloadsWithoutWritingAnything() throws Exception {
        String token = token("alice");
        var own = accounts.findAllByOwnerIdOrderById(users.findByUsername("alice").orElseThrow().getId());
        BigDecimal sourceBefore = own.get(0).getBalance();
        BigDecimal destinationBefore = own.get(1).getBalance();
        long movementCount = movements.count();
        String url = "/api/transfers";
        String[] invalidPayloads = {
            "{\"sourceAccountId\":" + own.get(0).getId() + ",\"destinationAccountId\":" + own.get(1).getId() + ",\"amount\":0}",
            "{\"sourceAccountId\":" + own.get(0).getId() + ",\"destinationAccountId\":" + own.get(1).getId() + ",\"amount\":-1}",
            "{\"sourceAccountId\":" + own.get(0).getId() + ",\"destinationAccountId\":" + own.get(1).getId() + ",\"amount\":1.001}",
            "{\"destinationAccountId\":" + own.get(1).getId() + ",\"amount\":1.00}",
            "{\"sourceAccountId\":" + own.get(0).getId() + ",\"amount\":1.00}",
            "{\"sourceAccountId\":" + own.get(0).getId() + ",\"destinationAccountId\":" + own.get(1).getId() + "}"
        };
        for (String payload : invalidPayloads) {
            mvc.perform(post(url).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post(url).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":999999,\"destinationAccountId\":"
                                + own.get(1).getId() + ",\"amount\":1.00}"))
                .andExpect(status().isNotFound());
        mvc.perform(post(url).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":" + own.get(0).getId() + ",\"destinationAccountId\":999999,\"amount\":1.00}"))
                .andExpect(status().isNotFound());

        assertEquals(0, accounts.findById(own.get(0).getId()).orElseThrow().getBalance().compareTo(sourceBefore));
        assertEquals(0, accounts.findById(own.get(1).getId()).orElseThrow().getBalance().compareTo(destinationBefore));
        assertEquals(movementCount, movements.count());
    }

    @Test
    void invalidAndInsufficientTransfersDoNotChangeBalancesOrMovements() throws Exception {
        String token = token("alice");
        var own = accounts.findAllByOwnerIdOrderById(users.findByUsername("alice").orElseThrow().getId());
        BigDecimal before = own.get(0).getBalance();
        long movementCount = movements.count();
        String url = "/api/transfers";
        mvc.perform(post(url).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":" + own.get(0).getId() + ",\"destinationAccountId\":"
                                + own.get(1).getId() + ",\"amount\":999999.00}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(url).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":" + own.get(0).getId() + ",\"destinationAccountId\":"
                                + own.get(1).getId() + ",\"amount\":1.001}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(url).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":" + own.get(0).getId() + ",\"destinationAccountId\":"
                                + own.get(0).getId() + ",\"amount\":1.00}"))
                .andExpect(status().isBadRequest());
        Long bobId = users.findByUsername("bob").orElseThrow().getId();
        Long bobAccountId = accounts.findAllByOwnerIdOrderById(bobId).get(0).getId();
        mvc.perform(post(url).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":" + own.get(0).getId() + ",\"destinationAccountId\":"
                                + bobAccountId + ",\"amount\":1.00}"))
                .andExpect(status().isNotFound());
        assertEquals(0, accounts.findById(own.get(0).getId()).orElseThrow().getBalance().compareTo(before));
        assertEquals(movementCount, movements.count());
    }

    @Test
    void movementDateFilterIsInclusiveByCalendarDayAndOwnerScoped() throws Exception {
        String token = token("alice");
        Long checkingId = accounts.findAllByOwnerIdOrderById(users.findByUsername("alice").orElseThrow().getId())
                .get(0).getId();
        String toYesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1).toString();
        mvc.perform(get("/api/accounts/" + checkingId + "/movements?from=2000-01-01&to=" + toYesterday)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/accounts/" + checkingId + "/movements?from=2025-12-31&to=2025-01-01")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void movementFiltersWorkIndependentlyAndAreSortedNewestFirst() throws Exception {
        String token = token("alice");
        Long checkingId = accounts.findAllByOwnerIdOrderById(users.findByUsername("alice").orElseThrow().getId())
                .get(0).getId();
        MvcResult allMovements = mvc.perform(get("/api/accounts/" + checkingId + "/movements")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2))).andReturn();
        JsonNode all = mapper.readTree(allMovements.getResponse().getContentAsString());
        assertFalse(Instant.parse(all.get(0).get("occurredAt").asText())
                .isBefore(Instant.parse(all.get(1).get("occurredAt").asText())));

        String recentStart = LocalDate.now(ZoneOffset.UTC).minusDays(3).toString();
        mvc.perform(get("/api/accounts/" + checkingId + "/movements?from=" + recentStart)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("WITHDRAWAL"));
        String beforeRecent = LocalDate.now(ZoneOffset.UTC).minusDays(3).toString();
        mvc.perform(get("/api/accounts/" + checkingId + "/movements?to=" + beforeRecent)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("DEPOSIT"));
        mvc.perform(get("/api/accounts/" + checkingId + "/movements?from=not-a-date")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void dateRangeIncludesBothCalendarDayBoundaries() throws Exception {
        String token = token("alice");
        BankAccount checking = accounts.findAllByOwnerIdOrderById(users.findByUsername("alice").orElseThrow().getId())
                .get(0);
        LocalDate rangeDate = LocalDate.now(ZoneOffset.UTC).minusDays(10);
        Instant firstMoment = rangeDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant lastMoment = rangeDate.plusDays(1).atStartOfDay().minusSeconds(1).toInstant(ZoneOffset.UTC);
        Instant followingDay = rangeDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        movements.save(new Movement(checking, MovementType.DEPOSIT, BigDecimal.ONE, "Inclusive range start",
                "test-date-start", firstMoment));
        movements.save(new Movement(checking, MovementType.DEPOSIT, BigDecimal.ONE, "Inclusive range end",
                "test-date-end", lastMoment));
        movements.save(new Movement(checking, MovementType.DEPOSIT, BigDecimal.ONE, "Exclusive next day",
                "test-date-next", followingDay));

        mvc.perform(get("/api/accounts/" + checking.getId() + "/movements?from=" + rangeDate + "&to=" + rangeDate)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].description").value("Inclusive range end"))
                .andExpect(jsonPath("$[1].description").value("Inclusive range start"));
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void rejectsCurrencyMismatchAndDestinationBalanceOverflowWithoutChanges() throws Exception {
        String token = token("alice");
        var own = accounts.findAllByOwnerIdOrderById(users.findByUsername("alice").orElseThrow().getId());
        BankAccount source = own.get(0);
        BankAccount destination = own.get(1);
        BigDecimal sourceBefore = source.getBalance();
        BigDecimal destinationBefore = destination.getBalance();
        long movementsBefore = movements.count();
        BankAccount usd = accounts.save(new BankAccount(source.getOwner(), "DEMO-ALICE-USD",
                "USD demo account", new BigDecimal("100.00"), "USD"));

        mvc.perform(post("/api/transfers").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":" + source.getId() + ",\"destinationAccountId\":"
                                + usd.getId() + ",\"amount\":1.00}"))
                .andExpect(status().isBadRequest());

        destination.setBalance(new BigDecimal("99999999999999999.99"));
        accounts.save(destination);
        mvc.perform(post("/api/transfers").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":" + source.getId() + ",\"destinationAccountId\":"
                                + destination.getId() + ",\"amount\":1.00}"))
                .andExpect(status().isBadRequest());

        assertEquals(0, accounts.findById(source.getId()).orElseThrow().getBalance().compareTo(sourceBefore));
        assertEquals(0, movements.count() - movementsBefore);
        destination.setBalance(destinationBefore);
        accounts.save(destination);
        accounts.delete(usd);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void rollsBackBalanceAndMovementWhenMovementPersistenceFails() throws Exception {
        String token = token("alice");
        var own = accounts.findAllByOwnerIdOrderById(users.findByUsername("alice").orElseThrow().getId());
        BigDecimal sourceBefore = own.get(0).getBalance();
        BigDecimal destinationBefore = own.get(1).getBalance();
        long movementCount = movements.count();
        doThrow(new IllegalStateException("Simulated movement persistence failure"))
                .when(movements).save(any(Movement.class));

        mvc.perform(post("/api/transfers").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":" + own.get(0).getId() + ",\"destinationAccountId\":"
                                + own.get(1).getId() + ",\"amount\":5.00}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"));

        assertEquals(0, accounts.findById(own.get(0).getId()).orElseThrow().getBalance().compareTo(sourceBefore));
        assertEquals(0, accounts.findById(own.get(1).getId()).orElseThrow().getBalance().compareTo(destinationBefore));
        assertEquals(movementCount, movements.count());
    }

    private String token(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new Login(username, "Demo1234!"))))
                .andExpect(status().isOk()).andReturn();
        JsonNode json = mapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }
    private String bearer(String token) { return "Bearer " + token; }
    private record Login(String username, String password) {}
}
