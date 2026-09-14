package com.example.webauthn;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.webauthn4j.*;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.WebAuthn4JHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;

import java.util.Set;

public class MainVerticle extends AbstractVerticle {

  // --- Konfiguration über Umgebungsvariablen (mit sinnvollen Dev-Defaults) ---
  private final String mongoUri = env("MONGO_URI", "mongodb://localhost:27017");
  private final String mongoDb = env("MONGO_DB", "webauthn_demo");
  private final String rpId = env("RP_ID", "localhost");
  private final String rpName = env("RP_NAME", "Vert.x WebAuthN4J Demo");
  private final String frontendOrigin = env("FRONTEND_ORIGIN", "https://localhost:5173");
  private final int port = Integer.parseInt(env("PORT", "8080"));

  @Override
  public void start(io.vertx.core.Promise<Void> startPromise) {
    MongoClient mongoClient = MongoClient.createShared(vertx, new JsonObject()
      .put("connection_string", mongoUri)
      .put("db_name", mongoDb));

    MongoCredentialStorage storage = new MongoCredentialStorage(mongoClient);

    WebAuthn4J webAuthn4J = WebAuthn4J.create(vertx, new WebAuthn4JOptions()
        .setRelyingParty(new RelyingParty().setId(rpId).setName(rpName))
        // Discoverable Credential erzwingen -> Username-less Login möglich
        .setResidentKey(ResidentKey.REQUIRED)
        .setUserVerification(UserVerification.REQUIRED))
      .credentialStorage(storage);

    Router router = Router.router(vertx);

    // WebAuthn4JHandler.setOrigin() erwartet - anders als unser CorsHandler oben -
    // GENAU EINEN Origin (keine kommagetrennte Liste). Läuft der Stack also über
    // mehrere gültige Origins (z.B. HTTP-Smoketest + HTTPS via Caddy), kann nur
    // der erste davon tatsächlich Passkeys registrieren/verifizieren. Das ist der
    // Haupt-Trade-off gegenüber der manuellen Low-Level-Variante (siehe master),
    // die dafür pro Request den tatsächlichen Origin-Header ausliest.
    String primaryOrigin = frontendOrigin.split(",")[0].trim();

    CorsHandler cors = CorsHandler.create()
      .allowCredentials(true)
      .allowedMethods(Set.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.OPTIONS))
      .allowedHeaders(Set.of("Content-Type"));
    for (String origin : frontendOrigin.split(",")) {
      cors.addOrigin(origin.trim());
    }
    router.route().handler(cors);

    router.route().handler(BodyHandler.create());
    router.route().handler(
      SessionHandler.create(LocalSessionStore.create(vertx))
        .setCookieSameSite(CookieSameSite.LAX));
      // Hinweis: Session-Cookies sind in Vert.x per default bereits HttpOnly (nicht konfigurierbar)

    // Höherwertige Handler-Variante: verdrahtet Register-/Login-Optionen und die
    // gemeinsame Response-Verifikation (Attestation UND Assertion laufen über
    // dieselbe Route) automatisch, inkl. Challenge/User-Handling in der Session.
    WebAuthn4JHandler webAuthnHandler = WebAuthn4JHandler.create(webAuthn4J)
      .setOrigin(primaryOrigin)
      .setupCredentialsCreateCallback(router.post("/api/webauthn/register/options"))
      .setupCredentialsGetCallback(router.post("/api/webauthn/login/options"))
      .setupCallback(router.post("/api/webauthn/response"));

    // Als AuthenticationHandler geeicht: prüft den (session-persistierten)
    // angemeldeten User und liefert sonst automatisch 401.
    router.get("/api/me").handler(webAuthnHandler).handler(this::me);
    router.post("/api/logout").handler(this::logout);

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(port)
      .onSuccess(server -> {
        System.out.println("HTTP server started on port " + server.actualPort());
        startPromise.complete();
      })
      .onFailure(startPromise::fail);
  }

  private void me(RoutingContext ctx) {
    ctx.json(new JsonObject().put("username", ctx.user().principal().getString("username")));
  }

  private void logout(RoutingContext ctx) {
    ctx.session().destroy();
    ctx.response().setStatusCode(204).end();
  }

  private static String env(String key, String fallback) {
    String v = System.getenv(key);
    return v == null || v.isBlank() ? fallback : v;
  }
}
