package com.sanigest.catalog.application.port.out.persistence;

import com.sanigest.catalog.application.dto.ClientReferences.Option;
import com.sanigest.catalog.application.dto.ClientReferences.ReceiptType;
import java.util.List;

public interface ClientReferenceRepository {
  List<Option> countries();

  List<Option> provinces(String country);

  List<Option> municipalities(String country, String province);

  List<ReceiptType> receiptTypes();

  boolean activeReceiptType(String code);
}
