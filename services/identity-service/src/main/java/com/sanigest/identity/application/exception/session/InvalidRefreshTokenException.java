package com.sanigest.identity.application.exception.session;

public class InvalidRefreshTokenException extends RuntimeException {
  public InvalidRefreshTokenException() {
    super("Invalid refresh token");
  }
}
