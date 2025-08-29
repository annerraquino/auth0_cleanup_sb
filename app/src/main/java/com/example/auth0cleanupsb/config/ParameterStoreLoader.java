// app/src/main/java/com/example/auth0cleanupsb/config/ParameterStoreLoader.java
package com.example.auth0cleanupsb.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.Parameter;

import java.util.HashMap;
import java.util.Map;

@Component
public class ParameterStoreLoader {
  private static final Logger log = LoggerFactory.getLogger(ParameterStoreLoader.class);
  private final AppProperties props;

  public ParameterStoreLoader(AppProperties props) {
    this.props = props;
  }

  @PostConstruct
  public void load() {
    String prefix = normalize(props.getParamPrefix());
    if (prefix == null) {
      log.info("APP_PARAM_PREFIX not set; skipping SSM load.");
      return;
    }

    String regionEnv = System.getenv().getOrDefault("AWS_REGION", "us-east-1");
    Region region = Region.of(regionEnv);
    try (SsmClient ssm = SsmClient.builder()
        .region(region)
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build()) {

      Map<String,String> kv = new HashMap<>();
      String nextToken = null;
      do {
        var req = GetParametersByPathRequest.builder()
            .path(prefix)
            .recursive(true)
            .withDecryption(true)
            .nextToken(nextToken)
            .build();
        var resp = ssm.getParametersByPath(req);
        for (Parameter p : resp.parameters()) {
          String name = p.name().substring(prefix.length()); // strip the prefix
          kv.put(name, p.value());
        }
        nextToken = resp.nextToken();
      } while (nextToken != null && !nextToken.isBlank());

      // Merge into props IF blank (env vars still win)
      setIfBlank(() -> props.getAuth0Domain(),       props::setAuth0Domain,       kv.get("AUTH0_DOMAIN"));
      setIfBlank(() -> props.getAuth0Audience(),     props::setAuth0Audience,     kv.get("AUTH0_AUDIENCE"));

      // support both CLIENT_ID vs CLIENTID
      String clientId = firstNonBlank(kv.get("AUTH0_CLIENT_ID"), kv.get("AUTH0_CLIENTID"));
      setIfBlank(() -> props.getAuth0ClientId(),     props::setAuth0ClientId,     clientId);

      // support both CLIENT_SECRET vs CLIENTSECRET
      String clientSecret = firstNonBlank(kv.get("AUTH0_CLIENT_SECRET"), kv.get("AUTH0_CLIENTSECRET"));
      setIfBlank(() -> props.getAuth0ClientSecret(), props::setAuth0ClientSecret, clientSecret);

      setIfBlank(() -> props.getS3Bucket(),          props::setS3Bucket,          kv.get("S3_BUCKET"));
      setIfBlank(() -> props.getInputS3Key(),        props::setInputS3Key,        kv.get("INPUT_S3_KEY"));
      // support both S3_KEY and OUTPUT_S3_KEY
      String outKey = firstNonBlank(kv.get("S3_KEY"), kv.get("OUTPUT_S3_KEY"));
      setIfBlank(() -> props.getOutputS3Key(),       props::setOutputS3Key,       outKey);

      log.info("SSM loaded from prefix '{}': domain='{}', audience='{}', clientId='{}', s3Bucket='{}', inputKey='{}', outputKey='{}'",
          prefix,
          nz(props.getAuth0Domain()),
          nz(props.getAuth0Audience()),
          tailMask(props.getAuth0ClientId()),
          nz(props.getS3Bucket()),
          nz(props.getInputS3Key()),
          nz(props.getOutputS3Key())
      );

    } catch (Exception e) {
      log.error("Failed to load parameters from SSM prefix '{}': {}", prefix, e.toString());
    }
  }

  private static String normalize(String pfx) {
    if (pfx == null || pfx.isBlank()) return null;
    String s = pfx.trim();
    if (!s.startsWith("/")) s = "/" + s;
    if (!s.endsWith("/")) s = s + "/";
    return s;
  }

  private static void setIfBlank(SupplierLike getter, Setter setter, String value) {
    if (value == null || value.isBlank()) return;
    String cur = getter.get();
    if (cur == null || cur.isBlank()) setter.set(value);
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    if (b != null && !b.isBlank()) return b;
    return null;
  }

  private static String nz(String s) { return (s == null || s.isBlank()) ? "<blank>" : s; }
  private static String tailMask(String s) {
    if (s == null || s.isBlank()) return "<blank>";
    int keep = Math.min(4, s.length());
    return "****" + s.substring(s.length() - keep);
  }

  @FunctionalInterface private interface SupplierLike { String get(); }
  @FunctionalInterface private interface Setter { void set(String v); }
}
