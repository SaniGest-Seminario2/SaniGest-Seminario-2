package com.sanigest.catalog.application;

import com.sanigest.catalog.domain.Client;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

public interface ClientRepository {
  void insert(Client client);

  Optional<Client> find(UUID companyId, UUID id);

  Client update(UUID companyId, UUID id, UnaryOperator<Client> change);

  ClientPage search(UUID companyId, String search, String status, int page, int size);
}
