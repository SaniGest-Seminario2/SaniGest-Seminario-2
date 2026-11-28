package com.sanigest.catalog.application.port.out.persistence.client;

import com.sanigest.catalog.application.dto.client.ClientPage;
import com.sanigest.catalog.application.dto.client.ClientSearchCriteria;
import com.sanigest.catalog.domain.model.client.Client;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

public interface ClientRepository {
  record Counts(long total, long active) {}

  Counts counts(UUID companyId);

  void insert(Client client);

  Optional<Client> find(UUID companyId, UUID id);

  Client update(UUID companyId, UUID id, UnaryOperator<Client> change);

  ClientPage search(UUID companyId, ClientSearchCriteria criteria, int page, int size);
}
