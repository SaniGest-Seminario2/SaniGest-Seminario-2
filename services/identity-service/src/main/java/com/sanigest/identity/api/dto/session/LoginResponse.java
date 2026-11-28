package com.sanigest.identity.api.dto.session;

public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    String refreshToken,
    long refreshExpiresIn) {
  public static LoginResponse from(
      com.sanigest.identity.application.dto.session.AccessToken token) {
    return new LoginResponse(
        token.value(),
        token.type(),
        token.expiresIn(),
        token.refreshToken(),
        token.refreshExpiresIn());
  }
}
