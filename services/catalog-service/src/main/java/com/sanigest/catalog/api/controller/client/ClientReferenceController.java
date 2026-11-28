package com.sanigest.catalog.api.controller.client;

import com.sanigest.catalog.application.dto.client.ClientReferences.Option;
import com.sanigest.catalog.application.dto.client.ClientReferences.ReceiptType;
import com.sanigest.catalog.application.usecase.client.ClientReferenceData;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clients/reference-data")
class ClientReferenceController {
  private final ClientReferenceData references;

  ClientReferenceController(ClientReferenceData references) {
    this.references = references;
  }

  @GetMapping("/countries")
  List<Option> countries() {
    return references.countries();
  }

  @GetMapping("/provinces")
  List<Option> provinces(@RequestParam(defaultValue = "DO") String countryCode) {
    return references.provinces(countryCode);
  }

  @GetMapping("/municipalities")
  List<Option> municipalities(
      @RequestParam(defaultValue = "DO") String countryCode, @RequestParam String provinceCode) {
    return references.municipalities(countryCode, provinceCode);
  }

  @GetMapping("/receipt-types")
  List<ReceiptType> receiptTypes() {
    return references.receiptTypes();
  }
}
