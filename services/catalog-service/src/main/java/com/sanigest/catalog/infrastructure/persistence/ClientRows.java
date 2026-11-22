package com.sanigest.catalog.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.repository.Repository;

interface ClientRows extends Repository<ClientRow, UUID> {
  <S extends ClientRow> S saveAndFlush(S row);
}
