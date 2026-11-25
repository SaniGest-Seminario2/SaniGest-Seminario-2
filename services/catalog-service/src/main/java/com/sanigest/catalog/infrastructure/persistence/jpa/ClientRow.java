package com.sanigest.catalog.infrastructure.persistence.jpa;

import com.sanigest.catalog.domain.enums.ClientPaymentTerms;
import com.sanigest.catalog.domain.enums.ClientStatus;
import com.sanigest.catalog.domain.enums.ClientType;
import com.sanigest.catalog.domain.model.Client;
import com.sanigest.catalog.domain.model.ClientDetails;
import com.sanigest.catalog.domain.model.ClientProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
  private ClientStatus status;

  @Column(nullable = false)
  private long revision;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private ClientType clientType;

  @Column(length = 32)
  private String alternatePhone;

  @Column(length = 2)
  private String countryCode;

  @Column(length = 8)
  private String provinceCode;

  @Column(length = 8)
  private String municipalityCode;

  @Column(length = 32)
  private String preferredReceiptType;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private ClientPaymentTerms paymentTerms;

  private UUID categoryId;

  @Column(precision = 19, scale = 2)
  private BigDecimal creditLimit;

  @Column(length = 1000)
  private String observations;

  @Column(insertable = false, nullable = false)
  private boolean enforceTaxKey;

  protected ClientRow() {}

  Client toDomain() {
    var profile =
        clientType == null
            ? null
            : new ClientProfile(
                clientType,
                alternatePhone,
                countryCode,
                provinceCode,
                municipalityCode,
                preferredReceiptType,
                paymentTerms,
                categoryId,
                creditLimit,
                observations);
    return new Client(
        id, companyId, new ClientDetails(name, taxId, email, phone, address, profile), status);
  }

  boolean enforcesTaxKey() {
    return enforceTaxKey;
  }

  long revision() {
    return revision;
  }

  ClientRow(Client client) {
    id = client.id();
    companyId = client.companyId();
    name = client.details().name();
    taxId = client.details().taxId();
    email = client.details().email();
    phone = client.details().phone();
    address = client.details().address();
    status = client.status();
    var profile = client.details().profile();
    if (profile != null) {
      clientType = profile.clientType();
      alternatePhone = profile.alternatePhone();
      countryCode = profile.countryCode();
      provinceCode = profile.provinceCode();
      municipalityCode = profile.municipalityCode();
      preferredReceiptType = profile.preferredReceiptType();
      paymentTerms = profile.paymentTerms();
      categoryId = profile.categoryId();
      creditLimit = profile.creditLimit();
      observations = profile.observations();
    }
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
