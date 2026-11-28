package com.sanigest.finance.application.exception.movement;

public class InvalidManualMovement extends RuntimeException {
  public InvalidManualMovement(String message) {
    super(message);
  }
}
