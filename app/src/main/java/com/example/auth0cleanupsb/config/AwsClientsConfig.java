// app/src/main/java/com/example/auth0cleanupsb/config/AwsClientsConfig.java
package com.example.auth0cleanupsb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AwsClientsConfig {

  @Bean
  public S3Client s3Client() {
    // Use env var if present; default to us-east-1
    String region = System.getenv().getOrDefault("AWS_REGION", "us-east-1");
    return S3Client.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }
}
