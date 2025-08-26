package com.example.auth0cleanupsb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
  private String paramPrefix = "/auth0-cleanup/";
  public static class S3 {
    private String bucket;
    private String key = "output/deleted_users.csv";
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
  }
  public static class Auth0 {
    private String domain;
    private String audience;
    private String clientId;
    private String clientSecret;
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
  }

  private S3 s3 = new S3();
  private Auth0 auth0 = new Auth0();

  public String getParamPrefix() { return paramPrefix; }
  public void setParamPrefix(String paramPrefix) { this.paramPrefix = paramPrefix; }
  public S3 getS3() { return s3; }
  public Auth0 getAuth0() { return auth0; }
}
