// app/src/main/java/com/example/auth0cleanupsb/batch/io/S3CsvUserReader.java
package com.example.auth0cleanupsb.batch.io;

import com.example.auth0cleanupsb.batch.model.UserDeleteRecord;
import com.example.auth0cleanupsb.config.AppProperties;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class S3CsvUserReader implements ItemStreamReader<UserDeleteRecord> {
  private static final Logger log = LoggerFactory.getLogger(S3CsvUserReader.class);

  private final S3Client s3;
  private final AppProperties props;    // <-- read bucket/key from here in open()
  private final boolean header;

  private ResponseInputStream<GetObjectResponse> in;
  private CSVParser parser;
  private Iterator<CSVRecord> it;

  public S3CsvUserReader(S3Client s3, AppProperties props, boolean header) {
    this.s3 = s3;
    this.props = props;
    this.header = header;
  }

  @Override
  public void open(ExecutionContext ctx) throws ItemStreamException {
    String bucket = nz(props.getS3Bucket());
    String key    = nz(props.getInputS3Key());
    try {
      if (bucket.isBlank()) throw new IllegalStateException("S3 bucket is blank");
      if (key.isBlank())    throw new IllegalStateException("S3 key is blank");

      log.info("Opening S3 CSV s3://{}/{}", bucket, key);

      in = s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());

      CSVFormat fmt = header
          ? CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build()
          : CSVFormat.DEFAULT.builder().setHeader("user_id","email","ssoid").build();

      parser = new CSVParser(new InputStreamReader(in, StandardCharsets.UTF_8), fmt);
      it = parser.iterator();
    } catch (Exception e) {
      throw new ItemStreamException("Failed to open S3 CSV s3://" + bucket + "/" + key, e);
    }
  }

  @Override public UserDeleteRecord read() {
    if (it == null || !it.hasNext()) return null;
    CSVRecord r = it.next();
    String userId = getField(r, "user_id", 0);
    String email  = getField(r, "email",   1);
    String ssoid  = getField(r, "ssoid",   2);
    return new UserDeleteRecord(clean(userId), clean(ssoid), clean(email));
  }

  @Override public void update(ExecutionContext ctx) {}
  @Override public void close() {
    try { if (parser != null) parser.close(); } catch (Exception ignored) {}
    try { if (in != null) in.close(); } catch (Exception ignored) {}
  }

  private static String getField(CSVRecord r, String name, int pos) {
    if (r.isMapped(name)) return nz(r.get(name)).trim();
    if (pos < r.size())   return nz(r.get(pos)).trim();
    return "";
  }
  private static String clean(String v) {
    String s = nz(v).trim();
    if (s.startsWith("'")) s = s.substring(1);
    if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))
      s = s.substring(1, s.length()-1);
    return s;
  }
  private static String nz(String s) { return s == null ? "" : s; }
}
