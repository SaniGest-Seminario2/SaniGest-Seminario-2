package com.sanigest.catalog.infrastructure.persistence;

import com.sanigest.catalog.domain.Client;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(schema = "catalog", name = "clients")
class ClientRow implements Persistable<UUID> {
  @Id private UUID id;

  @Column(nullable = false)
  private UUID companyId;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(length = 64)
  private String taxId;

  @Column(length = 320)
  private String email;

  @Column(length = 32)
  private String phone;

  @Column(length = 255)
  private String address;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Client.Status status;

  protected ClientRow() {}

  ClientRow(Client client) {
    id = client.id();
    companyId = client.companyId();
    name = client.details().name();
    taxId = client.details().taxId();
    email = client.details().email();
    phone = client.details().phone();
    address = client.details().address();
    status = client.status();
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    // The adapter only constructs rows for insertion with a server-assigned UUID.
    return true;
  }
}
