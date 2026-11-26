package com.sanigest.catalog.domain.model;

import com.sanigest.catalog.domain.enums.ClientStatus;
import com.sanigest.catalog.domain.exception.InvalidClientPatch;
import java.util.UUID;

public record ClientCategory(
    UUID id, UUID companyId, String code, String name, ClientStatus status) {
  public static final String DEFAULT_CODE = "REGULAR";

  public ClientCategory {
    if (code == null
        || !code.matches("[A-Z][A-Z0-9_]{0,31}")
        || name == null
        || name.isBlank()
        || name.length() > 100
        || name.indexOf(0) >= 0
        || status == null
        || (DEFAULT_CODE.equals(code) && status == ClientStatus.INACTIVE)) {
      throw new InvalidClientPatch();
    }
    name = name.strip();
  }
}
