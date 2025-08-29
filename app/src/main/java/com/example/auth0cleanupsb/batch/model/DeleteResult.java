package com.example.auth0cleanupsb.batch.model;

public class DeleteResult {
  private final String ssoid;
  private final String email;
  private final String auth0UserId;
  private final String status;              // e.g., DELETED, DRY_RUN, NOT_FOUND, ERROR
  private final String deactivationFlag;    // "Y" or "N"
  private final String lastUpdateTimestamp; // ISO-8601 string
  private final String error;               // nullable; message text

  public DeleteResult(
      String ssoid,
      String email,
      String auth0UserId,
      String status,
      String deactivationFlag,
      String lastUpdateTimestamp,
      String error) {
    this.ssoid = nz(ssoid);
    this.email = nz(email);
    this.auth0UserId = nz(auth0UserId);
    this.status = nz(status);
    this.deactivationFlag = nz(deactivationFlag);
    this.lastUpdateTimestamp = nz(lastUpdateTimestamp);
    this.error = error; // keep nulls distinct for CSV quoting
  }

  public String getSsoid() { return ssoid; }
  public String getEmail() { return email; }
  public String getAuth0UserId() { return auth0UserId; }
  public String getStatus() { return status; }
  public String getDeactivationFlag() { return deactivationFlag; }
  public String getLastUpdateTimestamp() { return lastUpdateTimestamp; }
  public String getError() { return error; }

  private static String nz(String s) { return s == null ? "" : s; }
}
