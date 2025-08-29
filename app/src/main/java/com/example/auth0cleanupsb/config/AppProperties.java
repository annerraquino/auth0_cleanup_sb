package com.example.auth0cleanupsb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppProperties {

  // If set, we will read missing values from SSM under this prefix (e.g., /auth0-cleanup-sb/)
  @Value("${APP_PARAM_PREFIX:}")
  private String paramPrefix;

  // Auth0
  @Value("${APP_AUTH0_DOMAIN:}")
  private String auth0Domain;              // e.g. dev-xxxx.us.auth0.com (no scheme)

  @Value("${APP_AUTH0_AUDIENCE:}")
  private String auth0Audience;            // e.g. https://dev-xxxx.us.auth0.com/api/v2/

  @Value("${APP_AUTH0_CLIENTID:}")
  private String auth0ClientId;

  @Value("${APP_AUTH0_CLIENTSECRET:}")
  private String auth0ClientSecret;

  // S3 (output + input)
  @Value("${APP_S3_BUCKET:}")
  private String s3Bucket;

  @Value("${APP_S3_KEY:output/deleted_users.csv}")
  private String outputS3Key;

  @Value("${APP_INPUT_S3_KEY:input/users_to_delete.csv}")
  private String inputS3Key;

  // --- getters ---
  public String getParamPrefix() { return paramPrefix; }
  public String getAuth0Domain() { return auth0Domain; }
  public String getAuth0Audience() { return auth0Audience; }
  public String getAuth0ClientId() { return auth0ClientId; }
  public String getAuth0ClientSecret() { return auth0ClientSecret; }
  public String getS3Bucket() { return s3Bucket; }
  public String getOutputS3Key() { return outputS3Key; }
  public String getInputS3Key() { return inputS3Key; }

  // --- setters (allow SSM loader to populate after bean creation) ---
  public void setParamPrefix(String v) { this.paramPrefix = v; }
  public void setAuth0Domain(String v) { this.auth0Domain = v; }
  public void setAuth0Audience(String v) { this.auth0Audience = v; }
  public void setAuth0ClientId(String v) { this.auth0ClientId = v; }
  public void setAuth0ClientSecret(String v) { this.auth0ClientSecret = v; }
  public void setS3Bucket(String v) { this.s3Bucket = v; }
  public void setOutputS3Key(String v) { this.outputS3Key = v; }
  public void setInputS3Key(String v) { this.inputS3Key = v; }
}
