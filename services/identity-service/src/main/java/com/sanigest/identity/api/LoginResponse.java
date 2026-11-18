package com.sanigest.identity.api;

public record LoginResponse(
    String accessToken, String tokenType, long expiresIn, String refreshToken, long refreshExpiresIn) {
  static LoginResponse from(com.sanigest.identity.application.AccessToken token) {
    return new LoginResponse(token.value(), token.type(), token.expiresIn(), token.refreshToken(),
        token.refreshExpiresIn());
  }
}
