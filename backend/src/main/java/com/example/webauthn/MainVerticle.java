package com.example.webauthn;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
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
import io.vertx.ext.web.sstore.LocalSessionStore;

import java.util.Set;
import java.util.UUID;

public class MainVerticle extends AbstractVerticle {

  // --- Konfiguration über Umgebungsvariablen (mit sinnvollen Dev-Defaults) ---
  private final String mongoUri = env("MONGO_URI", "mongodb://localhost:27017");
  private final String mongoDb = env("MONGO_DB", "webauthn_demo");
  private final String rpId = env("RP_ID", "localhost");
  private final String rpName = env("RP_NAME", "Vert.x WebAuthN4J Demo");
  private final String frontendOrigin = env("FRONTEND_ORIGIN", "https://localhost:5173");
  private final int port = Integer.parseInt(env("PORT", "8080"));

  private WebAuthn4J webAuthn4J;

  @Override
  public void start(io.vertx.core.Promise<Void> startPromise) {
    MongoClient mongoClient = MongoClient.createShared(vertx, new JsonObject()
      .put("connection_string", mongoUri)
      .put("db_name", mongoDb));

    MongoCredentialStorage storage = new MongoCredentialStorage(mongoClient);

    webAuthn4J = WebAuthn4J.create(vertx, new WebAuthn4JOptions()
        .setRelyingParty(new RelyingParty().setId(rpId).setName(rpName))
        // Discoverable Credential erzwingen -> Username-less Login möglich
        .setResidentKey(ResidentKey.REQUIRED)
        .setUserVerification(UserVerification.REQUIRED))
      .credentialStorage(storage);

    Router router = Router.router(vertx);

    router.route().handler(
      CorsHandler.create()
        .addOrigin(frontendOrigin)
        .allowCredentials(true)
        .allowedMethods(Set.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.OPTIONS))
        .allowedHeaders(Set.of("Content-Type")));

    router.route().handler(BodyHandler.create());
    router.route().handler(
      SessionHandler.create(LocalSessionStore.create(vertx))
        .setCookieSameSite(CookieSameSite.LAX));
      // Hinweis: Session-Cookies sind in Vert.x per default bereits HttpOnly (nicht konfigurierbar)

    router.post("/api/webauthn/register/options").handler(this::registerOptions);
    router.post("/api/webauthn/register/verify").handler(this::registerVerify);
    router.post("/api/webauthn/login/options").handler(this::loginOptions);
    router.post("/api/webauthn/login/verify").handler(this::loginVerify);

    router.get("/api/me").handler(this::me);
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

  // --- Registrierung: Schritt 1 - Optionen für navigator.credentials.create() ---
  private void registerOptions(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    String email = body.getString("email");
    String displayName = body.getString("displayName", email);

    if (email == null || email.isBlank()) {
      ctx.response().setStatusCode(400).end(new JsonObject().put("error", "email required").encode());
      return;
    }

    JsonObject user = new JsonObject()
      .put("name", email)
      .put("displayName", displayName);

    webAuthn4J.createCredentialsOptions(user)
      .onSuccess(options -> {
        // Challenge + Username für den Verify-Schritt in der Session merken.
        // Niemals dem Client vertrauen, welche Challenge/User gerade "aktiv" ist.
        ctx.session().put("webauthn.challenge", options.getString("challenge"));
        ctx.session().put("webauthn.username", email);
        ctx.json(options);
      })
      .onFailure(err -> fail(ctx, err));
  }

  // --- Registrierung: Schritt 2 - Attestation-Response verifizieren + speichern ---
  private void registerVerify(RoutingContext ctx) {
    verifyAndAuthenticate(ctx, false);
  }

  // --- Login: Schritt 1 - Optionen für navigator.credentials.get() ---
  // Kein "email" im Body -> Discoverable-Credential-Flow (Username-less Login)
  private void loginOptions(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject() != null ? ctx.body().asJsonObject() : new JsonObject();
    String email = body.getString("email");

    webAuthn4J.getCredentialsOptions(email)
      .onSuccess(options -> {
        ctx.session().put("webauthn.challenge", options.getString("challenge"));
        if (email != null) {
          ctx.session().put("webauthn.username", email);
        } else {
          ctx.session().remove("webauthn.username");
        }
        ctx.json(options);
      })
      .onFailure(err -> fail(ctx, err));
  }

  // --- Login: Schritt 2 - Assertion-Response verifizieren ---
  private void loginVerify(RoutingContext ctx) {
    verifyAndAuthenticate(ctx, true);
  }

  private void verifyAndAuthenticate(RoutingContext ctx, boolean isLogin) {
    String challenge = ctx.session().get("webauthn.challenge");
    String username = ctx.session().get("webauthn.username"); // kann bei Discoverable-Login null sein

    if (challenge == null) {
      ctx.response().setStatusCode(400)
        .end(new JsonObject().put("error", "no pending challenge in session").encode());
      return;
    }

    JsonObject rawResponse = ctx.body().asJsonObject();

    WebAuthn4JCredentials credentials = new WebAuthn4JCredentials()
      .setWebauthn(rawResponse)
      .setUsername(username)
      .setChallenge(challenge)
      .setOrigin(frontendOrigin)
      .setDomain(rpId);

    webAuthn4J.authenticate(credentials)
      .onSuccess(user -> {
        ctx.session().remove("webauthn.challenge");
        // Server-seitiger Login-Status
        String resolvedUsername = user.principal().getString("username", username);
        ctx.session().put("userId", resolvedUsername);
        ctx.json(new JsonObject()
          .put("status", "ok")
          .put("username", resolvedUsername)
          .put("mode", isLogin ? "login" : "register"));
      })
      .onFailure(err -> fail(ctx, err));
  }

  private void me(RoutingContext ctx) {
    String userId = ctx.session().get("userId");
    if (userId == null) {
      ctx.response().setStatusCode(401).end(new JsonObject().put("error", "not authenticated").encode());
      return;
    }
    ctx.json(new JsonObject().put("username", userId));
  }

  private void logout(RoutingContext ctx) {
    ctx.session().destroy();
    ctx.response().setStatusCode(204).end();
  }

  private void fail(RoutingContext ctx, Throwable err) {
    ctx.response().setStatusCode(400)
      .end(new JsonObject().put("error", err.getMessage() == null ? err.toString() : err.getMessage()).encode());
  }

  private static String env(String key, String fallback) {
    String v = System.getenv(key);
    return v == null || v.isBlank() ? fallback : v;
  }

  @SuppressWarnings("unused")
  private static String newOpaqueId() {
    return UUID.randomUUID().toString();
  }
}
