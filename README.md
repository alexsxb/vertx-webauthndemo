# Passkey Demo: Vert.x 5.1.8 + WebAuthn4J + MongoDB + React

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

## Wichtige Hinweise / was du noch prüfen solltest

- **`ResidentKey.REQUIRED`** ist in `MainVerticle` gesetzt, damit wirklich
  Discoverable Credentials erzeugt werden (Voraussetzung für den
  Username-less Login). Falls dein Test-Authenticator (z.B. ältere
  Hardware-Keys) das nicht unterstützt, schlägt die Registrierung fehl –
  dann testweise auf `ResidentKey.PREFERRED` umstellen.
- Der Login-Status wird hier bewusst simpel über
  `ctx.session().put("userId", ...)` verwaltet, nicht über Vert.x'
  `AuthenticationHandler`-Kette – das hält das Beispiel überschaubar, ist
  für Produktion aber ggf. zu erweitern (z.B. `SecurityAuditLogger`,
  Rate-Limiting auf den `/options`-Endpunkten gegen Enumeration).
- **Alternative:** Vert.x bietet mit `WebAuthn4JHandler` auch eine
  höherwertige Handler-Variante, die drei Callback-Routen automatisch
  verdrahtet. Dieses Beispiel nutzt bewusst die direkt dokumentierten
  Low-Level-Methoden (`createCredentialsOptions`/`getCredentialsOptions`/
  `authenticate`), um jeden Schritt transparent zu halten.
- Vor Produktivbetrieb: MongoDB in Compose ohne Auth – für echten Einsatz
  unbedingt Zugangsdaten/TLS ergänzen, `signCount`-Anomalien behandeln
  (klonverdächtige Authenticatoren sperren) und Session-Cookies auf
  `Secure` umstellen, sobald HTTPS im Einsatz ist.
