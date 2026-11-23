package com.sanigest.catalog.infrastructure.persistence.jpa;

import com.sanigest.catalog.domain.enums.CatalogItemType;
import com.sanigest.catalog.domain.model.CatalogItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(schema = "catalog", name = "catalog_items")
class CatalogItemRow implements Persistable<UUID> {
  @Id private UUID id;

  @Column(name = "company_id", nullable = false, updatable = false)
  private UUID companyId;

  @Column(nullable = false, length = CatalogItem.NAME_LIMIT)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private CatalogItemType type;

  @Column(
      nullable = false,
      precision = CatalogItem.PRICE_PRECISION,
      scale = CatalogItem.PRICE_SCALE)
  private BigDecimal price;

  @Column(nullable = false, length = 3, updatable = false)
  private String currency;

  @Column(nullable = false)
  private boolean active;

  @Column(length = CatalogItem.DESCRIPTION_LIMIT)
  private String description;

  @Column(nullable = false)
  private long revision;

  @Transient private boolean newRow = true;

  protected CatalogItemRow() {}

  CatalogItemRow(CatalogItem item) {
    id = item.id();
    companyId = item.companyId();
    name = item.name();
    type = item.type();
    price = item.price();
    currency = item.currency();
    active = item.active();
    description = item.description();
  }

  CatalogItem toDomain() {
    return new CatalogItem(id, companyId, name, type, price, active, description);
  }

  long revision() {
    return revision;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    // Assigned UUIDs must use persist on creation. Loaded rows already exist.
    return newRow;
  }

  @PostPersist
  @PostLoad
  private void markExisting() {
    newRow = false;
  }
}
