# Passkey Demo: Vert.x 5.1.8 + WebAuthn4J + MongoDB + React

> **Dieser Branch (`webauthn4j-handler`)** nutzt statt der manuellen
> Low-Level-Routen den höherwertigen `WebAuthn4JHandler` (`vertx-web`), der
> Register-/Login-Optionen und die Response-Verifikation automatisch
> verdrahtet. Kompakterer Code, aber ein echter Trade-off:
> `setOrigin()` akzeptiert nur **einen** Origin, nicht mehrere wie unser
> `CorsHandler` – bei mehreren gültigen Frontend-Origins (siehe
> `docker-compose.yml`) funktioniert die Passkey-Verifikation nur gegen den
> zuerst konfigurierten. Attestation (Register) und Assertion (Login) laufen
> außerdem über denselben Endpunkt (`POST /api/webauthn/response`) statt
> getrennter `.../register/verify` / `.../login/verify` Routen, und `/api/me`
> ist direkt über den Handler als `AuthenticationHandler` gesichert (401 ohne
> gültige Session) statt über einen manuellen Session-Check.
> Die manuelle Variante mit voller Kontrolle über Origin-Matching pro Request
> bleibt auf `master`.

Minimales, lauffähiges Beispiel für Passkey-Registrierung und -Login
(Discoverable Credentials) mit:

- **Backend:** Vert.x 5.1.8, `vertx-auth-webauthn4j`, `vertx-mongo-client`
- **Frontend:** React (Vite) + `@simplewebauthn/browser`
- **Persistenz:** MongoDB (via Docker Compose)

## 1. MongoDB starten

```bash
docker compose up -d
```

Startet MongoDB auf Port `27017` und optional mongo-express (UI) auf
`http://localhost:8081` zum Anschauen der gespeicherten Authenticator-Dokumente.

## 2. Backend starten

```bash
cd backend
mvn compile exec:java -Dexec.mainClass=com.example.webauthn.Main
# oder: mvn package && java -jar target/webauthn-backend-fat.jar
```

Relevante Umgebungsvariablen (alle optional, Defaults siehe `MainVerticle.java`):

| Variable          | Default                     | Bedeutung                          |
|-------------------|------------------------------|-------------------------------------|
| `MONGO_URI`       | `mongodb://localhost:27017`  | Mongo-Connection-String            |
| `MONGO_DB`        | `webauthn_demo`               | Datenbankname                       |
| `RP_ID`           | `localhost`                   | WebAuthn Relying Party ID (Domain) |
| `RP_NAME`         | `Vert.x WebAuthN4J Demo`      | Anzeigename                         |
| `FRONTEND_ORIGIN` | `https://localhost:5173`      | Erwarteter Origin (CORS + WebAuthn)|
| `PORT`            | `8080`                        | Backend-Port                        |

## 3. Frontend starten

```bash
cd frontend
npm install
npm run dev
```

Öffne `https://localhost:5173` (der Vite-Dev-Server läuft dank
`@vitejs/plugin-basic-ssl` über HTTPS mit einem automatisch erzeugten
selbstsignierten Zertifikat). Beim ersten Aufruf zeigt der Browser eine
Zertifikatswarnung – einmalig akzeptieren.

**Warum HTTPS auch lokal?** Browser behandeln `localhost` zwar selbst als
"secure context", sodass `navigator.credentials` auch über `http://` zur
Verfügung steht. Passwortmanager-Erweiterungen wie **KeePassXC-Browser**
prüfen aber zusätzlich das Origin-Protokoll und klinken sich in
`navigator.credentials.create/get` nur auf echten `https://`-Seiten ein –
auf `http://localhost` bleiben sie stumm, ohne Fehler oder Popup. Deshalb
läuft der Dev-Server jetzt über HTTPS.

## Ablauf

**Registrierung:**
1. `POST /api/webauthn/register/options` → `createCredentialsOptions()`,
   Challenge wird in der Server-Session gemerkt
2. Browser: `startRegistration()` → `navigator.credentials.create()`
3. `POST /api/webauthn/register/verify` → `webAuthn4J.authenticate()` prüft
   die Attestation und persistiert den Authenticator via
   `MongoCredentialStorage.storeCredential()`

**Login (Discoverable/Username-less, wenn E-Mail-Feld leer bleibt):**
1. `POST /api/webauthn/login/options` → `getCredentialsOptions(null)`
2. Browser: `startAuthentication()` → Authenticator zeigt dem Nutzer die
   lokal gespeicherten Passkeys für diese Domain zur Auswahl
3. `POST /api/webauthn/login/verify` → `authenticate()` prüft die Assertion,
   `MongoCredentialStorage.updateCounter()` aktualisiert den Replay-Counter

## Containerisiert: kompletter Stack via docker-compose

`backend` und `frontend` haben je ein Multi-Stage-`Dockerfile`
(Backend: Maven-Build → `azul/zulu-openjdk-alpine` JRE-Runtime; Frontend:
`npm run build` → nginx-Alpine, das `/api/*` intern an `backend:8080`
weiterleitet, sodass im Container-Stack **kein CORS** nötig ist).

Einmalig ein selbstsigniertes Dev-Zertifikat erzeugen (reines `openssl`,
kein mkcert/Systeminstall nötig):

```bash
./certs/generate.sh
```

Dann den Stack starten:

```bash
docker compose up -d --build backend frontend caddy
```

- `http://localhost:8082` – reiner HTTP-Smoketest (kein TLS, kein KeePassXC).
- `https://localhost:9443` – über **Caddy** TLS-terminiert (nutzt das
  Zertifikat aus `certs/`, per Bind-Mount reingereicht) → hier reagieren
  Passwortmanager-Erweiterungen wie KeePassXC-Browser. Zertifikatswarnung
  beim ersten Aufruf einmalig akzeptieren (selbstsigniert, wie beim
  Vite-Dev-Server).

### Ports im Compose-Stack

| Port  | Service         | Zweck                                                        |
|-------|-----------------|---------------------------------------------------------------|
| 9443  | `caddy`         | HTTPS (TLS-terminiert) – Haupteinstieg, u.a. für KeePassXC     |
| 8082  | `frontend`      | HTTP-Smoketest direkt auf nginx (ohne TLS)                     |
| 8080  | `backend`       | Vert.x-Backend direkt (z.B. für API-Tests ohne Frontend-Proxy) |
| 8081  | `mongo-express` | Web-UI zum Anschauen der gespeicherten Authenticator-Dokumente |
| 27017 | `mongo`         | MongoDB (direkter Zugriff, z.B. via `mongosh`)                 |

Für den reinen Passkey-Flow reichen `9443` (bzw. `8082`) – die übrigen Ports
sind optional für Debugging/Inspektion.

Caddy terminiert TLS und reicht alles an den `frontend`-Container weiter,
der wie gehabt `/api/*` intern an `backend:8080` proxied – die Origin-Prüfung
im Backend (`FRONTEND_ORIGIN`, kommagetrennt für mehrere erlaubte Origins)
lässt beide Wege (8082 und 9443) gleichzeitig zu.

Der `npm run dev`-Workflow (Vite, ebenfalls HTTPS) bleibt unabhängig davon
bestehen und ist der schnellere Loop für reine Frontend-Entwicklung.

## CI: Docker-Images bauen (GitHub Actions + Gitea Actions)

`.github/workflows/docker-build.yml` und `.gitea/workflows/docker-build.yml`
bauen bei jedem Push je ein Image für `backend/` und `frontend/`
(Gitea-Actions-Syntax ist absichtlich GitHub-Actions-kompatibel, daher fast
identischer Inhalt):

- **GitHub:** pusht nach `ghcr.io/<owner>/<repo>-{backend,frontend}` via
  dem automatischen `GITHUB_TOKEN` – funktioniert ohne weitere Konfiguration.
- **Gitea:** pusht nur, wenn die Repo-Variable `GITEA_REGISTRY`
  (z.B. `gitea.example.com`) und das Secret `REGISTRY_TOKEN`
  (Access-Token mit `write:package`) gesetzt sind. Ohne die beiden wird nur
  gebaut (Validierung), nicht gepusht – der Workflow läuft also bereits
  direkt nach der Migration, auch bevor die Registry eingerichtet ist.

## Wichtige Hinweise / was du noch prüfen solltest

- **`ResidentKey.REQUIRED`** ist in `MainVerticle` gesetzt, damit wirklich
  Discoverable Credentials erzeugt werden (Voraussetzung für den
  Username-less Login). Falls dein Test-Authenticator (z.B. ältere
  Hardware-Keys) das nicht unterstützt, schlägt die Registrierung fehl –
  dann testweise auf `ResidentKey.PREFERRED` umstellen.
- Der Login-Status läuft hier über Vert.x' `AuthenticationHandler`-Kette
  (`WebAuthn4JHandler` als Route-Handler vor `/api/me`), nicht über einen
  manuellen `ctx.session().put("userId", ...)`-Check wie auf `master` – das
  ist der idiomatische Weg, hat aber wie oben beschrieben den Single-Origin-
  Trade-off.
- Rate-Limiting auf den `/options`-Endpunkten gegen Enumeration ist hier
  (wie auf `master`) nicht implementiert – für echten Produktivbetrieb
  ergänzen.
- Vor Produktivbetrieb: MongoDB in Compose ohne Auth – für echten Einsatz
  unbedingt Zugangsdaten/TLS ergänzen, `signCount`-Anomalien behandeln
  (klonverdächtige Authenticatoren sperren) und Session-Cookies auf
  `Secure` umstellen, sobald HTTPS im Einsatz ist.
