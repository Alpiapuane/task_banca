# Conto Chiaro — applicazione bancaria dimostrativa

Applicazione didattica composta da frontend Angular, API REST Spring Boot e database MySQL. Consente di accedere con un profilo demo, consultare i propri conti e movimenti e simulare trasferimenti tra conti dello stesso cliente. **Non esegue operazioni bancarie reali e non usare dati o password personali.**

## Requisiti

- Node.js e npm (versioni compatibili con Angular 21; Node.js 20.19+ o 22.12+).
- Java 21 e Maven 3.9+.
- MySQL 8.0+.

## Configurazione e avvio

1. Avvia MySQL e crea il database `demo_banking` (oppure usa l'URL JDBC documentato in [backend/README.md](./backend/README.md)).
2. Configura le variabili backend `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`; per un ambiente persistente imposta anche `JWT_SIGNING_SECRET` con una chiave privata di almeno 32 byte. I dati demo vengono inizializzati dal backend quando la tabella utenti è vuota.
3. Avvia il backend dalla cartella `backend` con `mvn spring-boot:run`. L'API è disponibile su `http://localhost:8080`.
4. In un secondo terminale, dalla cartella `task_01`, esegui `npm install` la prima volta e poi `npm start`. L'interfaccia è disponibile su `http://localhost:4200`; il proxy Angular inoltra `/api` al backend locale.
5. Accedi con `alice` / `Demo1234!` (oppure `bob` con la stessa password). Sono credenziali pubbliche esclusivamente demo; non inserire account o password personali.

Per usare un backend su un altro host, aggiorna `proxy.conf.json`; non inserire token o credenziali reali nel codice.

## API REST

Le API scambiano JSON. Le API personali richiedono il token `accessToken` restituito dall'accesso nell'header `Authorization: Bearer <token>`.

| Metodo e percorso | Descrizione |
| --- | --- |
| `POST /api/auth/login` | Autentica il profilo demo. Body: `{"username":"alice","password":"Demo1234!"}`. La risposta contiene `accessToken`, `tokenType`, `expiresIn` e `username`. |
| `GET /api/accounts` | Restituisce esclusivamente i conti del cliente autenticato. |
| `GET /api/accounts/{id}/movements?from=YYYY-MM-DD&to=YYYY-MM-DD` | Movimenti del conto autorizzato, dal più recente al meno recente; i filtri inclusivi per data sono opzionali. |
| `POST /api/transfers` | Simula un trasferimento atomico tra due conti dello stesso cliente. Body: `{"sourceAccountId":1,"destinationAccountId":2,"amount":25.00}`. |

I dettagli dei payload, degli errori JSON, degli status HTTP, della configurazione MySQL e degli endpoint sono descritti in [backend/README.md](./backend/README.md).

## Test

- Frontend: `npm test -- --watch=false` (dalla cartella `task_01`).
- Backend: `mvn test` (dalla cartella `backend`).
- Build frontend: `npm run build`.

I test backend di integrazione coprono:

- credenziali demo, password hash, campi login obbligatori e risposta senza dati privati;
- autenticazione obbligatoria, token non valido e isolamento cliente per conti e movimenti;
- filtri data inclusivi, singoli o combinati, formati non validi e ordinamento più recente-prima;
- trasferimenti esatti con movimenti accoppiati, riferimento condiviso e rollback effettivo se fallisce l'inserimento del secondo movimento;
- importi zero, negativi o con più di due decimali, campi mancanti, conti uguali/inesistenti/non autorizzati e fondi insufficienti;
- invarianza di saldi e movimenti quando il trasferimento viene rifiutato, inclusi incompatibilità valuta, saldo insufficiente e superamento del saldo massimo.

I test frontend verificano schermata e invio login, ripristino sessione, caricamento con token, filtri, invio/conferma trasferimento, aggiornamento saldi, errori API senza perdita di stato, sessione scaduta e logout.

## CI/CD con GitHub Actions

Il workflow [ci-cd.yml](./.github/workflows/ci-cd.yml) si avvia automaticamente a ogni push, pull request e manualmente dalla scheda **Actions** di GitHub. Esegue in parallelo i test e la build Angular e i test/package Spring Boot. Se entrambi i job terminano correttamente, carica `conto-chiaro-frontend` e `conto-chiaro-backend` come artefatti scaricabili dalla run, conservati per 14 giorni. Questa pipeline verifica e prepara i pacchetti: non effettua il deploy su un servizio esterno.

Perché GitHub Actions rilevi il file, la cartella `task_01` deve essere la radice del repository GitHub (la directory `.github/workflows` deve trovarsi direttamente nella radice del repository).
