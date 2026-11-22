package com.sanigest.identity.application.exception;

public class InvalidRefreshTokenException extends RuntimeException {
  public InvalidRefreshTokenException() {
    super("Invalid refresh token");
  }
}
