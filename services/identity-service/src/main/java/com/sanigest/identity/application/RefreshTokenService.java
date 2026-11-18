package com.sanigest.identity.application;

import java.util.UUID;

public interface RefreshTokenService {
  IssuedRefreshToken issue(UUID userId);

  AccessToken refresh(String rawToken);

  void logout(String rawToken);

  record IssuedRefreshToken(String value, long expiresIn) {}
}
