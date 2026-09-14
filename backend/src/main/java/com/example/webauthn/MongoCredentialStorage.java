package com.example.webauthn;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.webauthn4j.Authenticator;
import io.vertx.ext.auth.webauthn4j.CredentialStorage;
import io.vertx.ext.mongo.MongoClient;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Persists WebAuthn4J {@link Authenticator}s in a MongoDB collection.
 *
 * Document shape is simply {@link Authenticator#toJson()} plus a Mongo "_id"
 * that mirrors the credential ID (credID), since credential IDs must be
 * unique per the CredentialStorage contract.
 */
public class MongoCredentialStorage implements CredentialStorage {

  private static final String COLLECTION = "authenticators";

  private final MongoClient mongo;

  public MongoCredentialStorage(MongoClient mongo) {
    this.mongo = mongo;
  }

  @Override
  public Future<List<Authenticator>> find(String userName, String credentialId) {
    if (userName == null && credentialId == null) {
      return Future.failedFuture(
        new IllegalArgumentException("At least one of userName or credentialId must be provided"));
    }

    JsonObject query = new JsonObject();
    if (userName != null) {
      query.put("username", userName);
    }
    if (credentialId != null) {
      query.put("credID", credentialId);
    }

    return mongo.find(COLLECTION, query)
      .map(docs -> docs.stream()
        .map(this::toAuthenticator)
        .collect(Collectors.toList()));
  }

  @Override
  public Future<Void> storeCredential(Authenticator authenticator) {
    JsonObject doc = authenticator.toJson().copy();
    // credID must be unique -> use it as Mongo primary key so duplicate
    // registrations fail loudly instead of silently overwriting.
    doc.put("_id", authenticator.getCredID());

    return mongo.insert(COLLECTION, doc).mapEmpty();
  }

  @Override
  public Future<Void> updateCounter(Authenticator authenticator) {
    JsonObject query = new JsonObject().put("_id", authenticator.getCredID());
    JsonObject update = new JsonObject()
      .put("$set", new JsonObject().put("counter", authenticator.getCounter()));

    return mongo.updateCollection(COLLECTION, query, update).mapEmpty();
  }

  private Authenticator toAuthenticator(JsonObject doc) {
    JsonObject copy = doc.copy();
    copy.remove("_id");
    return new Authenticator(copy);
  }
}
