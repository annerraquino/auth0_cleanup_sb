package com.example.auth0cleanupsb.auth0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class Auth0Client {
  private static final Logger log = LoggerFactory.getLogger(Auth0Client.class);
  private final ObjectMapper om = new ObjectMapper();
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public String getMgmtToken(String domain, String clientId, String clientSecret, String audience) throws Exception {
    String url = "https://" + strip(domain) + "/oauth/token";
    String body = """
      {"grant_type":"client_credentials","client_id":"%s","client_secret":"%s","audience":"%s"}
      """.formatted(clientId, clientSecret, audience);

    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(15))
        .header("content-type","application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();

    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() >= 300) {
      throw new RuntimeException("Auth0 token HTTP "+res.statusCode()+": "+res.body());
    }
    JsonNode node = om.readTree(res.body());
    if (!node.hasNonNull("access_token")) throw new RuntimeException("No access_token in token response");
    return node.get("access_token").asText();
  }

  public List<JsonNode> searchUsers(String domain, String token, String query) throws Exception {
    String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
    String url = "https://" + strip(domain) + "/api/v2/users?q=" + q + "&search_engine=v3&per_page=50";
    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(15))
        .header("authorization","Bearer "+token)
        .GET().build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

    if (res.statusCode() >= 300) {
      throw new RuntimeException("User search HTTP "+res.statusCode()+": "+res.body());
    }
    JsonNode arr = new ObjectMapper().readTree(res.body());
    List<JsonNode> list = new ArrayList<>();
    if (arr.isArray()) arr.forEach(list::add);
    if (list.isEmpty()) log.info("Cannot find user with query: {}", query);
    return list;
  }

  public void deleteUser(String domain, String token, String userId) throws Exception {
    String url = "https://" + strip(domain) + "/api/v2/users/" + URLEncoder.encode(userId, StandardCharsets.UTF_8);
    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(15))
        .header("authorization","Bearer "+token)
        .DELETE().build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() >= 300) {
      throw new RuntimeException("Delete HTTP "+res.statusCode()+": "+res.body());
    }
  }

  private static String strip(String d){ return d.replaceFirst("^https?://","").replaceAll("/+$",""); }
}
