package com.sanigest.catalog.infrastructure.persistence.jpa;

import com.sanigest.catalog.domain.enums.SupplierStatus;
import com.sanigest.catalog.domain.model.Supplier;
import com.sanigest.catalog.domain.model.SupplierDetails;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(schema = "catalog", name = "suppliers")
class SupplierRow implements Persistable<UUID> {
  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID companyId;

  @Column(nullable = false, length = SupplierDetails.NAME_LIMIT)
  private String name;

  @Column(length = SupplierDetails.TAX_ID_LIMIT)
  private String taxId;

  @Column(nullable = false, length = SupplierDetails.CONTACT_LIMIT)
  private String contact;

  @Column(length = SupplierDetails.PHONE_LIMIT)
  private String phone;

  @Column(length = SupplierDetails.EMAIL_LIMIT)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private SupplierStatus status;

  protected SupplierRow() {}

  SupplierRow(Supplier supplier) {
    var details = supplier.details();
    id = supplier.id();
    companyId = supplier.companyId();
    name = details.name();
    taxId = details.taxId();
    contact = details.contact();
    phone = details.phone();
    email = details.email();
    status = supplier.status();
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    // The adapter constructs rows only for insertion with a server-assigned UUID.
    return true;
  }
}
