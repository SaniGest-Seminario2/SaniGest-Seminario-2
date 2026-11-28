package com.sanigest.sales.application.port.out.integration.company;

import java.util.UUID;

public interface InvoiceCompanyAccess {
  CompanyContext requireInvoiceCompany();

  record CompanyContext(UUID id, String name) {}
}
