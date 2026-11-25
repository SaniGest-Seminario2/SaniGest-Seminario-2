package com.sanigest.catalog.application.usecase.client;

import static com.sanigest.catalog.application.port.out.integration.CatalogAuthorization.Permission.CATALOG_READ;

import com.sanigest.catalog.application.dto.ClientReferences.Option;
import com.sanigest.catalog.application.dto.ClientReferences.ReceiptType;
import com.sanigest.catalog.application.port.out.persistence.ClientReferenceRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("T(com.sanigest.companyaccess.AuthenticatedActor).userId().isPresent()")
public class ClientReferenceData {
  private final ClientCompany company;
  private final ClientReferenceRepository references;

  ClientReferenceData(ClientCompany company, ClientReferenceRepository references) {
    this.company = company;
    this.references = references;
  }

  public List<Option> countries() {
    company.require(CATALOG_READ);
    return references.countries();
  }

  public List<Option> provinces(String country) {
    company.require(CATALOG_READ);
    return references.provinces(country);
  }

  public List<Option> municipalities(String country, String province) {
    company.require(CATALOG_READ);
    return references.municipalities(country, province);
  }

  public List<ReceiptType> receiptTypes() {
    company.require(CATALOG_READ);
    return references.receiptTypes();
  }
}
