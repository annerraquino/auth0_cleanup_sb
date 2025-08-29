package com.example.auth0cleanupsb.batch.io;

import com.example.auth0cleanupsb.batch.model.DeleteResult;
import com.example.auth0cleanupsb.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamWriter;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class S3CsvResultWriter implements ItemStreamWriter<DeleteResult> {
  private static final Logger log = LoggerFactory.getLogger(S3CsvResultWriter.class);

  private final S3Client s3;
  private final AppProperties props;

  private String bucket;   // resolved at open()
  private String key;      // resolved at open()
  private boolean headerPresent;

  public S3CsvResultWriter(S3Client s3, AppProperties props) {
    this.s3 = s3;
    this.props = props;
  }

  @Override
  public void open(ExecutionContext ctx) throws ItemStreamException {
    this.bucket = nz(props.getS3Bucket());
    this.key    = nz(props.getOutputS3Key());

    if (bucket.isBlank()) throw new ItemStreamException("S3 bucket is blank");
    if (key.isBlank())    throw new ItemStreamException("S3 key is blank");

    log.info("Opening S3 CSV writer s3://{}/{}", bucket, key);

    try {
      HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder()
          .bucket(bucket).key(key).build());
      headerPresent = head.contentLength() != null && head.contentLength() > 0;
    } catch (S3Exception e) {
      // For missing object HeadObject returns 404 (S3Exception)
      if (e.statusCode() == 404) {
        headerPresent = false;
      } else {
        throw new ItemStreamException("Failed to inspect existing S3 object s3://" + bucket + "/" + key, e);
      }
    } catch (Exception e) {
      throw new ItemStreamException("Failed to inspect existing S3 object s3://" + bucket + "/" + key, e);
    }
  }

  @Override
  public void write(Chunk<? extends DeleteResult> items) throws Exception {
    if (items == null || items.isEmpty()) return;

    StringBuilder sb = new StringBuilder(1024);

    if (!headerPresent) {
      sb.append("ssoid,email,auth0_user_id,status,deactivation_flag,last_update_timestamp,error\n");
      headerPresent = true;
    }

    for (DeleteResult r : items.getItems()) {
      String err = r.getError() == null ? "" : r.getError().replace("\"", "\"\"");
      sb.append(nz(r.getSsoid())).append(',')
        .append(nz(r.getEmail())).append(',')
        .append(nz(r.getAuth0UserId())).append(',')
        .append(nz(r.getStatus())).append(',')
        .append(nz(r.getDeactivationFlag())).append(',')
        .append(nz(r.getLastUpdateTimestamp())).append(',')
        .append('"').append(err).append('"')
        .append('\n');
    }

    byte[] newChunk = sb.toString().getBytes(StandardCharsets.UTF_8);
    byte[] toUpload;

    if (objectExists(bucket, key)) {
      byte[] existing = readAll(s3.getObject(GetObjectRequest.builder()
          .bucket(bucket).key(key).build()));
      toUpload = concat(existing, newChunk);
    } else {
      toUpload = newChunk;
    }

    s3.putObject(
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType("text/csv; charset=utf-8")
            .build(),
        RequestBody.fromBytes(toUpload)
    );
  }

  @Override public void update(ExecutionContext ctx) {}
  @Override public void close() {}

  private boolean objectExists(String b, String k) {
    try {
      s3.headObject(HeadObjectRequest.builder().bucket(b).key(k).build());
      return true;
    } catch (S3Exception e) {
      return e.statusCode() != 404 ? false : false; // return false on 404, false on other errors too
    }
  }

  private static byte[] readAll(ResponseInputStream<GetObjectResponse> in) throws Exception {
    try (in; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      byte[] buf = new byte[8192];
      int r;
      while ((r = in.read(buf)) >= 0) bos.write(buf, 0, r);
      return bos.toByteArray();
    }
  }

  private static byte[] concat(byte[] a, byte[] b) {
    byte[] out = new byte[a.length + b.length];
    System.arraycopy(a, 0, out, 0, a.length);
    System.arraycopy(b, 0, out, a.length, b.length);
    return out;
  }

  private static String nz(String s) { return s == null ? "" : s; }
}
