package com.sanigest.company.application;

import java.time.Instant;

/** Optional email adapter. Called after commit and must handle the token as a secret. */
public interface InvitationDelivery {
  void deliver(String recipient, String token, Instant expiresAt);
}
