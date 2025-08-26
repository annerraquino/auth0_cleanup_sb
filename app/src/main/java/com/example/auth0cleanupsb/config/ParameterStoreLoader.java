package com.example.auth0cleanupsb.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.Parameter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ParameterStoreLoader {
  private static final Logger log = LoggerFactory.getLogger(ParameterStoreLoader.class);
  private final AppProperties props;

  public ParameterStoreLoader(AppProperties props) {
    this.props = props;
  }

  @PostConstruct
  public void load() {
    // Only call SSM if required fields are missing in config/env.
    boolean need = isBlank(props.getAuth0().getDomain()) ||
                   isBlank(props.getAuth0().getClientId()) ||
                   isBlank(props.getAuth0().getClientSecret()) ||
                   isBlank(props.getS3().getBucket());

    if (!need) return;

    String prefix = props.getParamPrefix();
    if (!prefix.startsWith("/")) prefix = "/" + prefix;
    if (!prefix.endsWith("/")) prefix = prefix + "/";

    try (SsmClient ssm = SsmClient.builder()
        .region(Region.of(System.getenv().getOrDefault("AWS_REGION","us-east-1")))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build()) {

      String nextToken = null;
      Map<String,String> map = new java.util.HashMap<>();
      do {
        var req = GetParametersByPathRequest.builder()
            .path(prefix).withDecryption(true).recursive(false).nextToken(nextToken).build();
        var resp = ssm.getParametersByPath(req);
        for (Parameter p : resp.parameters()) {
          String key = p.name().substring(p.name().lastIndexOf('/') + 1);
          map.put(key, p.value());
        }
        nextToken = resp.nextToken();
      } while (nextToken != null);

      // Fill in missing values
      if (isBlank(props.getAuth0().getDomain()))   props.getAuth0().setDomain(map.getOrDefault("AUTH0_DOMAIN",""));
      if (isBlank(props.getAuth0().getAudience())) props.getAuth0().setAudience(map.getOrDefault("AUTH0_AUDIENCE",""));
      if (isBlank(props.getAuth0().getClientId())) props.getAuth0().setClientId(map.getOrDefault("AUTH0_CLIENT_ID",""));
      if (isBlank(props.getAuth0().getClientSecret())) props.getAuth0().setClientSecret(map.getOrDefault("AUTH0_CLIENT_SECRET",""));
      if (isBlank(props.getS3().getBucket()))      props.getS3().setBucket(map.getOrDefault("S3_BUCKET",""));
      if (isBlank(props.getS3().getKey()))         props.getS3().setKey(map.getOrDefault("S3_KEY","output/deleted_users.csv"));

      log.info("Loaded SSM params: {}", map.keySet().stream().collect(Collectors.joining(",")));
    } catch (Exception e) {
      log.warn("Parameter Store load skipped/failed: {}", e.getMessage());
    }

    // Derive audience from domain if still missing
    if (isBlank(props.getAuth0().getAudience()) && !isBlank(props.getAuth0().getDomain())) {
      props.getAuth0().setAudience("https://" + stripProtocol(props.getAuth0().getDomain()) + "/api/v2/");
    }
  }

  private static boolean isBlank(String s) { return s == null || s.isBlank(); }
  private static String stripProtocol(String d){ return d.replaceFirst("^https?://","").replaceAll("/+$",""); }
}
