package com.example.auth0cleanupsb.service;

import com.example.auth0cleanupsb.auth0.Auth0Client;
import com.example.auth0cleanupsb.config.AppProperties;
import com.example.auth0cleanupsb.s3.S3CsvWriter;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class CleanupService {
  private final AppProperties props;
  private final Auth0Client auth0;
  private final S3CsvWriter s3;

  public CleanupService(AppProperties props, Auth0Client auth0, S3CsvWriter s3) {
    this.props = props; this.auth0 = auth0; this.s3 = s3;
  }

  public Map<String,Object> deleteBySsoid(String ssoid, boolean dryRun, String deletedBy) throws Exception {
    var a = props.getAuth0();
    String audience = (a.getAudience()==null||a.getAudience().isBlank())
        ? "https://" + strip(a.getDomain()) + "/api/v2/"
        : a.getAudience();

    String token = auth0.getMgmtToken(a.getDomain(), a.getClientId(), a.getClientSecret(), audience);

    List<JsonNode> users = auth0.searchUsers(a.getDomain(), token, "identities.user_id:\""+ssoid+"\"");
    if (users.isEmpty()) {
      users = auth0.searchUsers(a.getDomain(), token, "app_metadata.ssoid:\""+ssoid+"\"");
    }
    if (users.isEmpty()) {
      return Map.of("message","Cannot find user", "ssoid", ssoid, "results", List.of());
    }

    List<Map<String,Object>> results = new ArrayList<>();
    StringBuilder csv = new StringBuilder();
    String now = Instant.now().toString();

    for (JsonNode u : users) {
      String userId = opt(u,"user_id");
      if (!dryRun) {
        auth0.deleteUser(a.getDomain(), token, userId);
      }

      results.add(Map.of("user_id", userId, "deleted", !dryRun));
      csv.append(buildCsvRow(ssoid, now, u, deletedBy));
    }

    if (!dryRun && props.getS3().getBucket()!=null && !props.getS3().getBucket().isBlank()) {
      s3.appendRows(props.getS3().getBucket(), props.getS3().getKey(), csv.toString());
    }

    return Map.of("message", dryRun? "Dry run complete":"Delete attempt complete",
                  "ssoid", ssoid,
                  "count", results.size(),
                  "results", results);
  }

  private static String buildCsvRow(String ssoid, String ts, JsonNode u, String deletedBy) {
    String providers = joinArray(u.path("identities"), "provider");
    String connections = joinArray(u.path("identities"), "connection");
    String row = String.join(",",
        csv(ssoid),
        csv("Y"),
        csv(ts),
        csv(opt(u,"user_id")),
        csv(opt(u,"email")),
        csv(firstNonBlank(opt(u,"name"), opt(u,"nickname"), opt(u,"username"))),
        csv(providers),
        csv(connections),
        csv(opt(u,"created_at")),
        csv(opt(u,"last_login")),
        csvNum(u.path("logins_count").asText(null)),
        csv(deletedBy)
    );
    return row + "\n";
  }

  private static String joinArray(JsonNode arr, String field){
    if (!arr.isArray()) return "";
    List<String> list = new ArrayList<>();
    for (JsonNode n: arr) list.add(opt(n, field));
    return String.join(";", list.stream().filter(s -> !s.isBlank()).toList());
  }

  private static String opt(JsonNode n, String field){
    return n.hasNonNull(field) ? n.get(field).asText() : "";
  }
  private static String firstNonBlank(String... v){
    for (String s: v) if (s!=null && !s.isBlank()) return s; return "";
  }
  private static String csv(String v){
    if (v==null) v="";
    if (v.contains(",") || v.contains("\"") || v.contains("\n"))
      return "\"" + v.replace("\"","\"\"") + "\"";
    return v;
  }
  private static String csvNum(String v){ return (v!=null && v.matches("\\d+")) ? v : ""; }
  private static String strip(String d){ return d.replaceFirst("^https?://","").replaceAll("/+$",""); }
}
