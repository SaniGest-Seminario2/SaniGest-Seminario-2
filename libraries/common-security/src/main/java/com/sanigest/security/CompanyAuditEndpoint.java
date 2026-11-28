package com.sanigest.security;

import com.sanigest.web.audit.AuditPage;
import com.sanigest.web.audit.AuditPage.Filters;
import com.sanigest.web.audit.AuditPage.Reader;
import java.util.UUID;
import java.util.function.Predicate;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.server.ResponseStatusException;

public abstract class CompanyAuditEndpoint {
  private final Predicate<UUID> authorized;
  private final Reader audit;

  protected CompanyAuditEndpoint(Predicate<UUID> authorized, Reader audit) {
    this.authorized = authorized;
    this.audit = audit;
  }

  @GetMapping
  public ResponseEntity<AuditPage> get(
      @RequestHeader HttpHeaders headers,
      @ParameterObject @ModelAttribute CompanyAuditParameters parameters) {
    UUID company;
    Filters filters;
    try {
      company = CompanySelector.resolve(headers.getOrEmpty(CompanySelector.HEADER), null, true);
      filters = parameters.filters();
    } catch (CompanySelector.InvalidContext | IllegalArgumentException invalid) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage());
    }
    if (!authorized.test(company)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Company audit access denied");
    }
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(audit.read(company, filters));
  }
}
