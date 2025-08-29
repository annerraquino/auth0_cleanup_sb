package com.example.auth0cleanupsb.batch.processor;

import com.example.auth0cleanupsb.auth0.Auth0Client;
import com.example.auth0cleanupsb.batch.model.DeleteResult;
import com.example.auth0cleanupsb.batch.model.UserDeleteRecord;
import org.springframework.batch.item.ItemProcessor;

import java.time.OffsetDateTime;

public class Auth0DeleteProcessor implements ItemProcessor<UserDeleteRecord, DeleteResult> {
  private final Auth0Client auth0;
  private final boolean dryRun;

  public Auth0DeleteProcessor(Auth0Client auth0, String dryRunParam) {
    this.auth0 = auth0;
    this.dryRun = Boolean.parseBoolean(dryRunParam);
  }

  @Override
  public DeleteResult process(UserDeleteRecord item) throws Exception {
    String userId = nz(item.getUserId());
    String ssoid  = nz(item.getSsoid());
    String email  = nz(item.getEmail());
    String ts = OffsetDateTime.now().toString();

    String status;
    String error = null;
    String effectiveUserId = userId;

    try {
      // 1) Prefer direct delete by user_id
      if (!userId.isBlank()) {
        if (!dryRun) auth0.deleteUserById(userId);
        status = dryRun ? "DRY_RUN" : "DELETED";
        return new DeleteResult(ssoid, email, userId, status, dryRun ? "N" : "Y", ts, null);
      }

      // 2) Fallback: find by SSOID
      if (!ssoid.isBlank()) {
        effectiveUserId = auth0.findUserIdBySsoid(ssoid);
      }

      // 3) Fallback: find by email
      if ((effectiveUserId == null || effectiveUserId.isBlank()) && !email.isBlank()) {
        effectiveUserId = auth0.findUserIdByEmail(email);
      }

      if (effectiveUserId == null || effectiveUserId.isBlank()) {
        status = "NOT_FOUND";
        return new DeleteResult(ssoid, email, null, status, "N", ts, null);
      }

      if (!dryRun) auth0.deleteUserById(effectiveUserId);
      status = dryRun ? "DRY_RUN" : "DELETED";
      return new DeleteResult(ssoid, email, effectiveUserId, status, dryRun ? "N" : "Y", ts, null);

    } catch (Exception e) {
      status = "ERROR";
      error = e.getMessage();
      return new DeleteResult(ssoid, email, effectiveUserId, status, "N", ts, error);
    }
  }

  private static String nz(String s) { return s == null ? "" : s.trim(); }
}
