package com.sanigest.identity.application.port.out.security;

import com.sanigest.identity.application.dto.AccessToken;
import java.util.UUID;

public interface TokenIssuer {

  AccessToken issue(UUID userId);
}
