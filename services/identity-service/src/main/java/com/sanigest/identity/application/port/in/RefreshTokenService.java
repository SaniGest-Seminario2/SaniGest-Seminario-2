package com.sanigest.identity.application.port.in;

import com.sanigest.identity.application.dto.AccessToken;
import com.sanigest.identity.application.dto.IssuedRefreshToken;
import java.util.UUID;

public interface RefreshTokenService {
  IssuedRefreshToken issue(UUID userId);

  AccessToken refresh(String rawToken);

  void logout(String rawToken);
}
