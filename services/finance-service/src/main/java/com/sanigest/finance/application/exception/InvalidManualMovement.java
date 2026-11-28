package com.sanigest.finance.application.exception;

public class InvalidManualMovement extends RuntimeException {
  public InvalidManualMovement(String message) {
    super(message);
  }
}
