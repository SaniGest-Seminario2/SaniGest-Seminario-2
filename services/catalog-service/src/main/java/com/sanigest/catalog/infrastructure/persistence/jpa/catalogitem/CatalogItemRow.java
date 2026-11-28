package com.sanigest.catalog.infrastructure.persistence.jpa.catalogitem;

import com.sanigest.catalog.domain.enums.catalogitem.CatalogItemType;
import com.sanigest.catalog.domain.model.catalogitem.CatalogItem;
import com.sanigest.catalog.domain.model.catalogitem.CatalogItemProfile;
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

  @Column(name = "sku", length = 64)
  private String sku;

  @Column(name = "barcode", length = 64)
  private String barcode;

  @Column(name = "category_id")
  private UUID categoryId;

  @Column(name = "unit_code", length = 32)
  private String unitCode;

  @Column(name = "estimated_cost", precision = 19, scale = 4)
  private BigDecimal estimatedCost;

  @Column(name = "tax_classification_code", length = 32)
  private String taxClassificationCode;

  @Column(name = "available_for_sale")
  private boolean availableForSale;

  @Column(name = "available_for_purchase")
  private Boolean availableForPurchase;

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
    sku = item.profile().sku();
    barcode = item.profile().barcode();
    categoryId = item.profile().categoryId();
    unitCode = item.profile().unitCode();
    estimatedCost = item.profile().estimatedCost();
    taxClassificationCode = item.profile().taxClassificationCode();
    availableForSale = item.profile().availableForSale();
    availableForPurchase = item.profile().availableForPurchase();
  }

  CatalogItem toDomain() {
    return new CatalogItem(
        id,
        companyId,
        name,
        type,
        price,
        active,
        description,
        new CatalogItemProfile(
            sku,
            barcode,
            categoryId,
            unitCode,
            estimatedCost,
            taxClassificationCode,
            availableForSale,
            availableForPurchase));
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
