# Demo banking API (educational)

A small Spring Boot 3 / Java 17 REST backend using MySQL and Spring Data JPA. It is a learning/demo application, **not** a production banking system. It has no real payment rail, interest calculation, regulatory controls, rate limiting, audit retention policy, or production-grade key rotation.

## Run locally

1. Install Java 17+ and Maven 3.9+, and start MySQL. The default connection creates `demo_banking` if needed and connects as local `root` with an empty password. Prefer a dedicated local DB user and set its values with environment variables:

   ```powershell
   $env:DB_URL='jdbc:mysql://localhost:3306/demo_banking?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
   $env:DB_USERNAME='demo_app'
   $env:DB_PASSWORD='local-only-password'
   ```

2. From this directory run `mvn spring-boot:run`. The API listens on `http://localhost:8080`.
3. For development, the app seeds `alice` and `bob`, each with password `Demo1234!`, sample accounts and dated movements, **only when the user table is empty**. Those public demo credentials must never be reused or deployed. Set `DEMO_DATA_ENABLED=false` to disable seeding.
4. Login using `POST /api/auth/login`, then send `Authorization: Bearer <accessToken>` on protected requests. Tokens expire after 60 minutes by default. Without `JWT_SIGNING_SECRET`, a cryptographically random per-process key is generated: tokens are invalidated at restart. For repeatable local tokens set a private random value of at least 32 bytes in the environment. In any deployed environment, provide and protect your own secret; do not put it in source control.

The Angular development origins `http://localhost:4200` and `http://127.0.0.1:4200` are allowed by CORS. Change the allow-list in `SecurityConfig` for another frontend origin. Schema updates use Hibernate `ddl-auto: update` for convenience; use managed, reviewed database migrations for real deployment.

## API

All responses are JSON. IDs are numeric, monetary fields are JSON numbers represented internally and persisted as exact `DECIMAL(19,2)`. Dates use ISO calendar dates (`YYYY-MM-DD`) in query parameters and UTC ISO-8601 timestamps in responses.

| Method / path | Auth | Purpose |
| --- | --- | --- |
| `POST /api/auth/login` | Public | Authenticate and return a bearer token |
| `GET /api/accounts` | Bearer | List only the caller's accounts |
| `GET /api/accounts/{accountId}` | Bearer | Read an owned account |
| `GET /api/accounts/{accountId}/movements?from=YYYY-MM-DD&to=YYYY-MM-DD` | Bearer | Owned account movements; either bound may be omitted, both dates inclusive |
| `POST /api/transfers` | Bearer | Transfer between two of the caller's accounts |

Login body:

```json
{ "username": "alice", "password": "Demo1234!" }
```

Login response:

```json
{ "accessToken": "<signed-token>", "tokenType": "Bearer", "expiresIn": 3600, "username": "alice" }
```

Transfer body:

```json
{ "sourceAccountId": 1, "destinationAccountId": 2, "amount": 25.50 }
```

The amount must be positive and have at most two decimal places; it is rejected rather than rounded. Source and destination must differ, be owned by the authenticated user, use the same currency, and the source must have sufficient funds. Transfers lock both account rows in ID order and commit the two balance changes plus paired debit/credit movements as one transaction. A failed request leaves all three unchanged. Cross-owner account references return `404` so account existence is not disclosed. Invalid input/date ranges return `400`, invalid credentials return `401`, and unexpected errors do not expose server details.

## Data model

| Table | Main columns and constraints |
| --- | --- |
| `app_users` | `id` primary key, unique `username`, BCrypt `password_hash` |
| `bank_accounts` | `id`, `owner_id` foreign key, unique `iban`, `label`, `balance DECIMAL(19,2)`, `currency CHAR(3)` |
| `account_movements` | `id`, `account_id` foreign key, `type`, `amount DECIMAL(19,2)`, description, transfer reference, UTC occurrence time; account/time index |

The ORM generates the physical schema from the entity mappings at startup. Seed account identifiers are intentionally conspicuous non-IBAN placeholders (`DEMO-ALICE-001`, `DEMO-ALICE-002`, `DEMO-BOB-001`); the JSON property remains named `iban` for the demo's existing API shape. Account and movement queries always include owner scope; the password hash is never returned by an API.

## Validation

Run `mvn test` from this directory. Integration tests use an in-memory H2 database in MySQL compatibility mode and cover:

- valid and invalid credentials, bcrypt password hashes, required login fields, invalid bearer tokens, and unauthenticated requests;
- per-customer account and movement authorization, including nonexistent and foreign-owned account IDs;
- movement ranges with both bounds, either bound alone, inclusive start/end calendar days, malformed/reversed dates, and newest-first ordering;
- exact transfer arithmetic, paired debit/credit movements with one shared reference, and response fields;
- rejected transfers for missing fields, zero/negative/over-precision amounts, missing/equal/foreign accounts, insufficient funds, currency mismatch, and destination balance overflow;
- unchanged balances and movement counts after rejected transfers, and a simulated database failure on the second movement insert to verify rollback of the first insert and both balance updates.
