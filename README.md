## User Management Service

Servizio di gestione utenti basato su Spring Boot, pensato per essere utilizzato in un contesto microservizi. Espone API REST per:
- creare, aggiornare, disabilitare e cancellare logicamente utenti
- elencare gli utenti con paginazione
- pubblicare eventi di dominio su RabbitMQ alla creazione di un utente

Le API sono protette tramite OAuth2 Resource Server (JWT) e integrate con un identity provider (es. Keycloak).

### Stack tecnologico

- Java 21
- Spring Boot 3 (Web, Validation, Data JPA, Security, OAuth2 Resource Server)
- PostgreSQL + Flyway per la persistenza
- RabbitMQ per la messaggistica degli eventi utente
- Springdoc OpenAPI per la documentazione REST
- H2 in‑memory database per i test di integrazione

---

## Build ed esecuzione

### Requisiti

- JDK 21 installato
- Maven 3.9+
- PostgreSQL in esecuzione (di default su `localhost:5432` con database `ums`)
- RabbitMQ in esecuzione per gli eventi utente

In alternativa è possibile utilizzare l’ambiente dockerizzato fornito dal progetto.

### Configurazione applicativa

La configurazione predefinita si trova in:
- `src/main/resources/application.yml`

Valori principali:
- `spring.datasource.url`: URL JDBC di PostgreSQL (default `jdbc:postgresql://localhost:5432/ums`)
- `spring.datasource.username` / `spring.datasource.password`: credenziali database
- `security.enabled`: abilita/disabilita la sicurezza (default `true`)
- `app.events.exchange` e `app.events.routing.userCreated`: configurazione exchange e routing key per gli eventi su RabbitMQ

Per ambienti diversi sono disponibili:
- `application-local.yml`
- `application-docker.yml`

Le proprietà possono essere sovrascritte da variabili d’ambiente o parametri `-Dspring.*` standard di Spring Boot.

### Avvio locale con Maven

Da root del progetto:

```bash
mvn spring-boot:run
```

Oppure build del JAR ed esecuzione:

```bash
mvn clean package
java -jar target/user-management-service-0.1.0-SNAPSHOT.jar
```

L’applicazione espone:
- API REST su `http://localhost:8080/api/users`
- OpenAPI/Swagger UI su `http://localhost:8080/swagger-ui.html`

### Avvio tramite Docker

È disponibile un `Dockerfile` per il servizio e un `docker-compose.yml` che orchestra database e RabbitMQ.

Esempio di avvio rapido:

```bash
docker compose up --build
```

Questo comando effettua la build dell’immagine e avvia i container necessari (database, RabbitMQ e servizio UMS) con la configurazione definita in `application-docker.yml`.

### Test automatici

Per eseguire l’intera suite di test (unitari + integrazione):

```bash
mvn test
```

I test utilizzano H2 in‑memory e disabilitano la sicurezza e Flyway dove opportuno tramite il profilo `test` (`application-test.yml`).

---

## Scelte tecniche principali

### Architettura e layering

- **Controller REST (`web`)**: espongono le API HTTP (es. `UserController`) e demandano la logica di business al service.
- **Service (`service`)**: incapsulano la logica di dominio (es. `UserService`) e orchestrano repository, validazione e pubblicazione eventi.
- **Repository (`repository`)**: interfacce Spring Data JPA verso il database (es. `UserRepository`).
- **Domain (`domain`)**: entità JPA e tipi di dominio (`User`, `Role`, `UserStatus`).
- **DTO/Mapper (`dto`, `mapper`)**: separano i contratti API dalle entità di persistenza.

Questa separazione consente di mantenere chiaro il boundary tra web layer, business logic e accesso ai dati.

### Persistenza e migrazioni

- JPA/Hibernate per l’accesso ai dati.
- PostgreSQL come database di riferimento.
- Flyway per le migrazioni (`db/migration`), con `ddl-auto=validate` in produzione per evitare modifiche automatiche allo schema.

Nei test viene utilizzato H2 con `ddl-auto=create-drop` per avere un database pulito ad ogni esecuzione.

### Sicurezza e autorizzazione

- Configurazione centralizzata in `SecurityConfig` con `SecurityFilterChain`.
- Quando `security.enabled=true`, il servizio funziona come **OAuth2 Resource Server** basato su JWT.
- Le autorizzazioni sono modellate come ruoli applicativi (`OWNER`, `MAINTAINER`, `OPERATOR`, `DEVELOPER`, `REPORTER`) mappati da claim JWT (`realm_access.roles`).
- Vengono utilizzati:
  - **regole a livello HTTP** in `SecurityConfig` per proteggere i path `/api/users/**`
  - **regole a livello metodo** tramite `@PreAuthorize` nel `UserController` per controllare l’accesso ai singoli endpoint.

Per semplificare i test di integrazione:
- il profilo `test` disabilita la sicurezza a livello HTTP
- i test usano `@WithMockUser` dove è necessario verificare il comportamento con ruoli specifici.

### Messaggistica con RabbitMQ

- La creazione di un utente pubblica un evento `UserCreatedEvent` su RabbitMQ tramite `AmqpTemplate`.
- Exchange e routing key sono configurabili via proprietà `app.events.*`.
- In caso di problemi nella pubblicazione, l’errore viene loggato ma non blocca la creazione dell’utente (fail‑safe).
- Nei test, `AmqpTemplate` viene sostituito da un mock per evitare dipendenze da un broker reale.

### Validazione e dominio

- Validazione degli input eseguita tramite Jakarta Bean Validation:
  - annotazioni standard (`@NotBlank`, `@Email`, ecc.) sui DTO.
  - annotazione personalizzata `@CodiceFiscale` con relativo validator.
- Vincoli di unicità applicativi su email e codice fiscale:
  - verificati nel service, con eccezioni dedicate (`ConflictException`) in caso di violazione.
- Cancellazione utente implementata come **soft delete**:
  - lo stato passa a `DELETED`
  - le query di lettura evitano gli utenti cancellati.

### Gestione errori e API

- `ErrorHandler` centralizza la traduzione delle eccezioni applicative in risposte HTTP significative:
  - `NotFoundException` → 404
  - `BadRequestException` → 400
  - `ConflictException` → 409
  - errori di validazione → 422 con dettagli dei campi.

Questo garantisce messaggi coerenti e facili da consumare dal client.

---

## Linee guida di leggibilità del codice

- Metodi brevi e con responsabilità chiara (es. `UserService` separa creazione, aggiornamento, disabilitazione, soft delete).
- Nomi espliciti per metodi e classi (es. `softDeleteUser`, `UserCreatedEvent`, `SecurityConfig`).
- Commenti solo nei punti non ovvi del flusso (es. mapping ruoli da JWT, salvataggio soft‑delete, pubblicazione eventi).

---

## Esempio di pipeline CI/CD di base

Una possibile pipeline CI/CD minima (ad esempio su GitHub Actions o GitLab CI) potrebbe prevedere tre fasi:

1. **Build & Test (CI)**
   - Trigger: ogni push o merge request verso il branch principale.
   - Step principali:
     - checkout del codice
     - setup JDK 21
     - esecuzione `mvn -B clean verify` (compilazione + test + verifica)
   - Obiettivo: assicurarsi che il codice sia compilabile e che tutti i test passino prima di procedere.

2. **Build immagine Docker**
   - Trigger: solo su branch principale/tag rilasciati.
   - Step principali:
     - build dell’immagine con il `Dockerfile` presente nella root del progetto
     - tagging dell’immagine (es. `registry.example.com/ums-service:${GIT_SHA}` e `:latest`)
     - push dell’immagine verso il container registry aziendale (GitHub Container Registry, GitLab Registry, ecc.).

3. **Deploy su ambiente di test/stage**
   - Trigger: manuale (approvazione) o automatico dopo una build riuscita su `main`.
   - Step principali:
     - aggiornamento dello stack Docker Compose / orchestratore (es. `docker compose pull && docker compose up -d` oppure applicazione di manifest Kubernetes)
     - eventuale esecuzione di health check sull’endpoint `/actuator/health`.

### Esempio (GitHub Actions, semplificato)

Configurazione indicativa di workflow (file `.github/workflows/ci.yml`):

```yaml
name: CI

on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]

jobs:
  build-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Setup JDK
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
      - name: Build & Test
        run: mvn -B clean verify
```