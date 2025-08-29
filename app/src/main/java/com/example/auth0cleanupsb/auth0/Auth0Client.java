// app/src/main/java/com/example/auth0cleanupsb/auth0/Auth0Client.java
package com.example.auth0cleanupsb.auth0;

import com.example.auth0cleanupsb.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class Auth0Client {

  private final AppProperties props;
  private final HttpClient http;
  private final ObjectMapper om = new ObjectMapper();

  public Auth0Client(AppProperties props) {
    this.props = props;
    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  /* =========================
     Public API
     ========================= */

  /** Exchange client credentials for a Management API access token. */
  public String getMgmtToken() throws Exception {
    String domain = normalizeDomain(props.getAuth0Domain());
    String audience = mgmtAudience(); // guaranteed to end with a trailing slash
    String clientId = nz(props.getAuth0ClientId());
    String clientSecret = nz(props.getAuth0ClientSecret());

    if (domain.isBlank()) throw new IllegalStateException("Missing AUTH0_DOMAIN");
    if (audience.isBlank()) throw new IllegalStateException("Missing AUTH0_AUDIENCE (https://<tenant>/api/v2/)");
    if (clientId.isBlank()) throw new IllegalStateException("Missing AUTH0_CLIENT_ID/CLIENTID");
    if (clientSecret.isBlank()) throw new IllegalStateException("Missing AUTH0_CLIENT_SECRET/CLIENTSECRET");

    String tokenUrl = "https://" + domain + "/oauth/token";
    String jsonBody = String.format(
        "{\"client_id\":\"%s\",\"client_secret\":\"%s\",\"audience\":\"%s\",\"grant_type\":\"client_credentials\"}",
        escapeJson(clientId), escapeJson(clientSecret), escapeJson(audience));

    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(tokenUrl))
        .timeout(Duration.ofSeconds(20))
        .header("content-type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
        .build();

    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() / 100 != 2) {
      throw new RuntimeException("Auth0 token HTTP " + resp.statusCode() + ": " + resp.body());
    }
    JsonNode node = om.readTree(resp.body());
    String token = node.path("access_token").asText(null);
    if (token == null || token.isBlank()) {
      throw new RuntimeException("Auth0 token missing access_token in response: " + resp.body());
    }
    return token;
  }

  /** Delete a user by Auth0 user_id. Requires Management API scope: delete:users */
  public void deleteUserById(String userId) throws Exception {
    if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId is blank");
    String token = getMgmtToken();

    String url = mgmtBase() + "/users/" + URLEncoder.encode(userId, StandardCharsets.UTF_8);
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(30))
        .header("authorization", "Bearer " + token)
        .DELETE()
        .build();

    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    int code = resp.statusCode();
    if (code == 204 || code == 200) return;         // success
    if (code == 404) throw new RuntimeException("Auth0 delete HTTP 404 (user not found): " + userId);
    throw new RuntimeException("Auth0 delete HTTP " + code + ": " + resp.body());
  }

  /** Find user_id by SSOID. Tries multiple likely metadata paths. Requires read:users (and ideally read:users_app_metadata). */
  public String findUserIdBySsoid(String ssoid) throws Exception {
    if (ssoid == null || ssoid.isBlank()) return null;
    String token = getMgmtToken();
    String v = ssoid.trim();

    return searchUserFirstMatch(token,
        "app_metadata.ssoid:\"" + v + "\"",
        "user_metadata.ssoid:\"" + v + "\"",
        "app_metadata.sso_id:\"" + v + "\"",
        "app_metadata.enterprise.ssoid:\"" + v + "\"",
        "ssoid:\"" + v + "\""
    );
  }

  /** Find user_id by email using /users-by-email. Requires read:users. */
  public String findUserIdByEmail(String email) throws Exception {
    if (email == null || email.isBlank()) return null;

    String token = getMgmtToken();
    String url = mgmtBase() + "/users-by-email?email=" + URLEncoder.encode(email.trim(), StandardCharsets.UTF_8);

    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(20))
        .header("authorization", "Bearer " + token)
        .GET()
        .build();

    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() / 100 != 2) {
      throw new RuntimeException("Auth0 users-by-email HTTP " + resp.statusCode() + ": " + resp.body());
    }

    JsonNode arr = om.readTree(resp.body());
    if (arr.isArray() && arr.size() > 0) {
      return arr.get(0).path("user_id").asText(null);
    }
    return null;
  }

  /* =========================
     Internal helpers
     ========================= */

  /** Audience WITH a trailing slash (required for token exchange). */
  private String mgmtAudience() {
    String aud = nz(props.getAuth0Audience()).trim();
    if (!aud.isBlank()) {
      if (!aud.endsWith("/")) aud = aud + "/";
      return aud;
    }
    String domain = normalizeDomain(props.getAuth0Domain());
    if (domain.isBlank()) return "";
    return "https://" + domain + "/api/v2/";
  }

  /** Base URL WITHOUT trailing slash (for REST calls). */
  private String mgmtBase() {
    String aud = nz(props.getAuth0Audience()).trim();
    if (!aud.isBlank()) {
      while (aud.endsWith("/")) aud = aud.substring(0, aud.length() - 1);
      return aud;
    }
    String domain = normalizeDomain(props.getAuth0Domain());
    if (domain.isBlank()) return "";
    return "https://" + domain + "/api/v2";
  }

  /** Run several queries and return the first matching user_id (or null). */
  private String searchUserFirstMatch(String token, String... queries) throws Exception {
    String base = mgmtBase();
    for (String q : queries) {
      String enc = URLEncoder.encode(q, StandardCharsets.UTF_8);
      String url = base + "/users?q=" + enc
          + "&search_engine=v3&fields=user_id,email,app_metadata,user_metadata&include_fields=true";

      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(Duration.ofSeconds(20))
          .header("authorization", "Bearer " + token)
          .GET()
          .build();

      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() / 100 != 2) {
        throw new RuntimeException("Auth0 search HTTP " + resp.statusCode() + ": " + resp.body());
      }

      JsonNode arr = om.readTree(resp.body());
      if (arr.isArray() && arr.size() > 0) {
        return arr.get(0).path("user_id").asText(null);
      }
    }
    return null;
  }

  /** Ensure domain is like dev-xxxxx.us.auth0.com (no scheme, no trailing slash). */
  private static String normalizeDomain(String domain) {
    if (domain == null) return "";
    String d = domain.trim();
    if (d.isEmpty()) return "";
    d = d.replaceFirst("^https?://", "");
    while (d.endsWith("/")) d = d.substring(0, d.length() - 1);
    return d;
  }

  private static String nz(String s) { return s == null ? "" : s; }

  /** Minimal JSON escaping for values we control (client id/secret/audience). */
  private static String escapeJson(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
