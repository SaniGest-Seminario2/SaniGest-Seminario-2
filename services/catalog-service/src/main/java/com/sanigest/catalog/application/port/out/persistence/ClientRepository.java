package com.sanigest.catalog.application.port.out.persistence;

import com.sanigest.catalog.application.dto.ClientPage;
import com.sanigest.catalog.application.dto.ClientSearchCriteria;
import com.sanigest.catalog.domain.model.Client;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

public interface ClientRepository {
  void insert(Client client);

  Optional<Client> find(UUID companyId, UUID id);

  Client update(UUID companyId, UUID id, UnaryOperator<Client> change);

  ClientPage search(UUID companyId, ClientSearchCriteria criteria, int page, int size);
}
