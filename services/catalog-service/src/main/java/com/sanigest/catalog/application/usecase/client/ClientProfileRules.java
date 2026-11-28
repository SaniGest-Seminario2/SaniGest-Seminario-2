package com.sanigest.catalog.application.usecase.client;

import com.sanigest.catalog.application.port.out.persistence.client.ClientCategoryRepository;
import com.sanigest.catalog.application.port.out.persistence.client.ClientReferenceRepository;
import com.sanigest.catalog.domain.enums.client.ClientStatus;
import com.sanigest.catalog.domain.exception.client.InvalidClientPatch;
import com.sanigest.catalog.domain.model.client.ClientDetails;
import com.sanigest.catalog.domain.model.client.ClientProfile;
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
      throw new InvalidClientPatch();
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
