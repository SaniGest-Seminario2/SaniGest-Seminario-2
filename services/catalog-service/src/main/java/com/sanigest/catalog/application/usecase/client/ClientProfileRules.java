package com.sanigest.catalog.application.usecase.client;

import com.sanigest.catalog.application.port.out.persistence.ClientCategoryRepository;
import com.sanigest.catalog.application.port.out.persistence.ClientReferenceRepository;
import com.sanigest.catalog.domain.enums.ClientStatus;
import com.sanigest.catalog.domain.exception.InvalidClientPatch;
import com.sanigest.catalog.domain.model.ClientDetails;
import com.sanigest.catalog.domain.model.ClientProfile;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ClientProfileRules {
  private final ClientCategoryRepository categories;
  private final ClientReferenceRepository references;

  ClientProfileRules(ClientCategoryRepository categories, ClientReferenceRepository references) {
    this.categories = categories;
    this.references = references;
  }

  ClientDetails prepareCreation(UUID companyId, ClientDetails details) {
    var profile = details.profile();
    if (profile == null) {
      return details;
    }
    if (profile.categoryId() == null) {
      categories.initialize(companyId);
      profile = profile.defaults(categories.defaultId(companyId));
    } else {
      profile = profile.defaults(null);
    }
    profile.requireComplete();
    validateSelection(companyId, null, profile);
    return details.withProfile(profile);
  }

  void validateSelection(UUID companyId, ClientProfile previous, ClientProfile next) {
    if (next == null) {
      return;
    }
    if ((previous == null || !Objects.equals(previous.categoryId(), next.categoryId()))
        && categories
            .lockForAssignment(companyId, next.categoryId())
            .filter(category -> category.status() == ClientStatus.ACTIVE)
            .isEmpty()) {
      throw new InvalidClientPatch();
    }
    if (next.preferredReceiptType() != null
        && (previous == null
            || !Objects.equals(previous.preferredReceiptType(), next.preferredReceiptType()))
        && !references.activeReceiptType(next.preferredReceiptType())) {
      throw new InvalidClientPatch();
    }
  }
}
