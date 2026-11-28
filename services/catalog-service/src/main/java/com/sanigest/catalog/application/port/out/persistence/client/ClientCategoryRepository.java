package com.sanigest.catalog.application.port.out.persistence.client;

import com.sanigest.catalog.domain.model.client.ClientCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientCategoryRepository {
  void initialize(UUID companyId);

  List<ClientCategory> list(UUID companyId);

  UUID defaultId(UUID companyId);

  Optional<ClientCategory> lockForAssignment(UUID companyId, UUID id);
}
