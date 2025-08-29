package com.example.auth0cleanupsb.service;

import com.example.auth0cleanupsb.auth0.Auth0Client;
import com.example.auth0cleanupsb.config.AppProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CleanupService {
  private final Auth0Client auth0;
  private final S3Client s3;
  private final AppProperties props;

  public CleanupService(Auth0Client auth0, S3Client s3, AppProperties props) {
    this.auth0 = auth0;
    this.s3 = s3;
    this.props = props;
  }

  /** Back-compat: controller that doesn't pass email can use this. */
  public Map<String, Object> deleteBySsoid(String ssoid, boolean dryRun) throws Exception {
    return deleteBySsoid(ssoid, dryRun, "");
  }

  /** Preferred: include email so it lands in the CSV output. */
  public Map<String, Object> deleteBySsoid(String ssoid, boolean dryRun, String email) throws Exception {
    String ts = OffsetDateTime.now().toString();
    String status;
    String userId = null;
    String err = null;

    try {
      userId = auth0.findUserIdBySsoid(ssoid);
      if (userId == null || userId.isBlank()) {
        status = "NOT_FOUND";
        appendCsv(ssoid, email, null, status, "N", ts, null);
        return resp(ssoid, status, userId, null);
      }
      if (dryRun) {
        status = "DRY_RUN";
      } else {
        auth0.deleteUserById(userId);
        status = "DELETED";
      }
      appendCsv(ssoid, email, userId, status, "Y", ts, null);
      return resp(ssoid, status, userId, null);
    } catch (Exception e) {
      err = e.getMessage();
      status = "ERROR";
      appendCsv(ssoid, email, userId, status, "N", ts, err);
      throw e;
    }
  }

  private Map<String, Object> resp(String ssoid, String status, String userId, String error) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("ssoid", ssoid);
    m.put("status", status);
    m.put("auth0_user_id", userId);
    if (error != null) m.put("error", error);
    return m;
  }

  private void appendCsv(String ssoid, String email, String userId, String status,
                         String deactFlag, String ts, String error) {
    String bucket = props.getS3Bucket();
    String key = props.getOutputS3Key();

    StringBuilder sb = new StringBuilder();
    sb.append(csv(ssoid)).append(',')
      .append(csv(email)).append(',')
      .append(csv(userId)).append(',')
      .append(csv(status)).append(',')
      .append(csv(deactFlag)).append(',')
      .append(csv(ts)).append(',')
      .append(csv(error)).append('\n');

    byte[] newChunk = sb.toString().getBytes(StandardCharsets.UTF_8);
    byte[] base = new byte[0];
    try {
      ResponseBytes<GetObjectResponse> existing =
          s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build());
      base = existing.asByteArray();
    } catch (S3Exception ignored) {
      // object may not exist yet
    }

    byte[] merged = new byte[base.length + newChunk.length];
    System.arraycopy(base, 0, merged, 0, base.length);
    System.arraycopy(newChunk, 0, merged, base.length, newChunk.length);

    s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
        RequestBody.fromBytes(merged));
  }

  private static String csv(String v) {
    if (v == null) return "";
    if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
      return "\"" + v.replace("\"", "\"\"") + "\"";
    }
    return v;
  }
}
