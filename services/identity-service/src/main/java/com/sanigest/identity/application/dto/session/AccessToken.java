package com.sanigest.identity.application.dto.session;

public record AccessToken(
    String value, String type, long expiresIn, String refreshToken, long refreshExpiresIn) {

  public AccessToken(String value, String type, long expiresIn) {
    this(value, type, expiresIn, null, 0);
  }
}
