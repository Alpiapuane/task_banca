package com.example.banking;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.banking.account.AccountRepository;
import com.example.banking.movement.MovementRepository;
import com.example.banking.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
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
    @Autowired MovementRepository movements;
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
    void requiresAuthenticationAndScopesAccountsToOwner() throws Exception {
        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
        String alice = token("alice");
        String bob = token("bob");
        mvc.perform(get("/api/accounts").header("Authorization", bearer(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].iban").value("DEMO-ALICE-001"))
                .andExpect(jsonPath("$[1].iban").value("DEMO-ALICE-002"));
        Long bobId = users.findByUsername("bob").orElseThrow().getId();
        Long bobAccountId = accounts.findAllByOwnerIdOrderById(bobId).get(0).getId();
        mvc.perform(get("/api/accounts/" + bobAccountId).header("Authorization", bearer(alice)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/accounts/" + bobAccountId + "/movements").header("Authorization", bearer(alice)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/accounts").header("Authorization", bearer(bob)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void transferIsExactAndCreatesPairedMovementsAtomically() throws Exception {
        String token = token("alice");
        var initial = accounts.findAllByOwnerIdOrderById(users.findByUsername("alice").orElseThrow().getId());
        BigDecimal sourceBefore = initial.get(0).getBalance();
        BigDecimal destinationBefore = initial.get(1).getBalance();
        mvc.perform(post("/api/transfers").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":" + initial.get(0).getId() + ",\"destinationAccountId\":"
                                + initial.get(1).getId() + ",\"amount\":12.34}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(12.34));
        assertEquals(0, accounts.findById(initial.get(0).getId()).orElseThrow().getBalance()
                .compareTo(sourceBefore.subtract(new BigDecimal("12.34"))));
        assertEquals(0, accounts.findById(initial.get(1).getId()).orElseThrow().getBalance()
                .compareTo(destinationBefore.add(new BigDecimal("12.34"))));
        assertEquals(2, movements.findAll().stream().filter(m -> m.getTransferReference() != null
                && !m.getTransferReference().startsWith("seed-")).count());
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
