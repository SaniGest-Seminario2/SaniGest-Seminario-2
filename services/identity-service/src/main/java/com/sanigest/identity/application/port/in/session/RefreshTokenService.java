package com.sanigest.identity.application.port.in.session;

import com.sanigest.identity.application.dto.session.AccessToken;
import com.sanigest.identity.application.dto.session.IssuedRefreshToken;
import java.util.UUID;

public interface RefreshTokenService {
  IssuedRefreshToken issue(UUID userId);

  AccessToken refresh(String rawToken);

  void logout(String rawToken);
}
