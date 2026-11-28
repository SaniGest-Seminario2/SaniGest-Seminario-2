package com.sanigest.identity.application.port.out.security.token;

import com.sanigest.identity.application.dto.session.AccessToken;
import java.util.UUID;

public interface TokenIssuer {

  AccessToken issue(UUID userId);
}
