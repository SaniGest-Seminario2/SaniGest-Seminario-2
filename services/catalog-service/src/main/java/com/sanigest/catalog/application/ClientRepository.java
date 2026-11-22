package com.sanigest.catalog.application;

import com.sanigest.catalog.domain.Client;
import java.util.UUID;

public interface ClientRepository {
  void insert(Client client);

  ClientPage search(UUID companyId, String search, String status, int page, int size);
}
