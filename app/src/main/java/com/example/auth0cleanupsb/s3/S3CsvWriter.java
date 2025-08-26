package com.example.auth0cleanupsb.s3;

import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class S3CsvWriter {
  private static final Logger log = LoggerFactory.getLogger(S3CsvWriter.class);
  private static final String HEADER = "ssoid,deactivation_flag,last_update_timestamp,user_id,email,name,providers,connections,created_at,last_login,logins_count,deleted_by\n";

  private final S3Client s3;

  public S3CsvWriter() {
    this.s3 = S3Client.builder()
        .region(Region.of(System.getenv().getOrDefault("AWS_REGION","us-east-1")))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }

  public void appendRows(String bucket, String key, String rows) throws Exception {
    String existing = "";
    try (InputStream in = s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
      existing = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (NoSuchKeyException e) {
      // ok new file
    } catch (SdkClientException e) {
      log.warn("s3 getObject warning: {}", e.getMessage());
    }

    boolean hasHeader = existing.trim().toLowerCase().startsWith(HEADER.substring(0, HEADER.indexOf('\n')).toLowerCase());
    String next = (hasHeader ? existing : (existing.isEmpty()? HEADER : (HEADER + existing))) + rows;

    s3.putObject(
        PutObjectRequest.builder().bucket(bucket).key(key).contentType("text/csv").build(),
        RequestBody.fromString(next, StandardCharsets.UTF_8)
    );
  }
}
