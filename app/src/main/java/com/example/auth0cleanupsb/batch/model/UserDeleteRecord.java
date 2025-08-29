package com.example.auth0cleanupsb.batch.model;

public class UserDeleteRecord {
  private final String userId;
  private final String ssoid;
  private final String email;

  public UserDeleteRecord(String userId, String ssoid, String email) {
    this.userId = userId == null ? "" : userId;
    this.ssoid  = ssoid  == null ? "" : ssoid;
    this.email  = email  == null ? "" : email;
  }

  public String getUserId() { return userId; }
  public String getSsoid()  { return ssoid; }
  public String getEmail()  { return email; }
}
