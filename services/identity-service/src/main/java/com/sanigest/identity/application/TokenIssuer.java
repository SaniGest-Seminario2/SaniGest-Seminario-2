package com.sanigest.identity.application;

import java.util.UUID;

public interface TokenIssuer {

  AccessToken issue(UUID userId);
}
