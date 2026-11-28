package com.sanigest.catalog.application.port.out.persistence.client;

import com.sanigest.catalog.application.dto.client.ClientReferences.Option;
import com.sanigest.catalog.application.dto.client.ClientReferences.ReceiptType;
import java.util.List;

public interface ClientReferenceRepository {
  List<Option> countries();

  List<Option> provinces(String country);

  List<Option> municipalities(String country, String province);

  List<ReceiptType> receiptTypes();

  boolean activeReceiptType(String code);
}
