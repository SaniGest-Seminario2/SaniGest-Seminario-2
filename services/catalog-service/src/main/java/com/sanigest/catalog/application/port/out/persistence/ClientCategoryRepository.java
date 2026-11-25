package com.sanigest.catalog.application.port.out.persistence;

import com.sanigest.catalog.domain.model.ClientCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientCategoryRepository {
  void initialize(UUID companyId);

  List<ClientCategory> list(UUID companyId);

  UUID defaultId(UUID companyId);

  Optional<ClientCategory> lockForAssignment(UUID companyId, UUID id);

  Optional<ClientCategory> lockForEdit(UUID companyId, UUID id);

  boolean insert(ClientCategory category);

  void update(ClientCategory category);
}
