package com.sanigest.catalog.application.dto;

public final class ClientReferences {
  private ClientReferences() {}

  public record Option(String code, String name) {}

  public record ReceiptType(String code, String name, String version, boolean active) {}
}
