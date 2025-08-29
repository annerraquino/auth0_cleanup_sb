// app/src/main/java/com/example/auth0cleanupsb/config/ConfigDiagnostics.java
package com.example.auth0cleanupsb.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ConfigDiagnostics {
  private static final Logger log = LoggerFactory.getLogger(ConfigDiagnostics.class);
  private final AppProperties props;

  public ConfigDiagnostics(AppProperties props) { this.props = props; }

  @PostConstruct
  public void logEffectiveConfig() {
    // Mask helpers
    String domain = nvl(props.getAuth0Domain());
    String audience = nvl(props.getAuth0Audience());
    String clientIdMasked = tailMask(props.getAuth0ClientId());      // e.g., ****...ABCD
    String clientSecretMasked = "*****";                              // never print secrets

    String bucket = nvl(props.getS3Bucket());
    String inputKey = nvl(props.getInputS3Key());
    String outputKey = nvl(props.getOutputS3Key());
    String prefix = nvl(props.getParamPrefix());
    String region = System.getenv().getOrDefault("AWS_REGION", "us-east-1");

    log.info("Effective config -> APP_PARAM_PREFIX='{}', AWS_REGION='{}'", prefix, region);
    log.info("Auth0 -> domain='{}', audience='{}', clientId='{}', clientSecret='{}'",
        domain, audience, clientIdMasked, clientSecretMasked);
    log.info("S3 -> bucket='{}', inputKey='{}', outputKey='{}'", bucket, inputKey, outputKey);
  }

  private static String nvl(String s) { return (s == null || s.isBlank()) ? "<blank>" : s; }

  private static String tailMask(String s) {
    if (s == null || s.isBlank()) return "<blank>";
    int keep = Math.min(4, s.length());
    return "****" + s.substring(s.length() - keep);
  }
}
