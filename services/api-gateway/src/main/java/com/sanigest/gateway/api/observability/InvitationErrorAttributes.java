package com.sanigest.gateway.api.observability;

import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webflux.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

@Component
class InvitationErrorAttributes extends DefaultErrorAttributes {
  @Override
  public Map<String, Object> getErrorAttributes(
      ServerRequest request, ErrorAttributeOptions options) {
    if (request.path().startsWith("/api/v1/invitations/")) {
      var attributes = super.getErrorAttributes(request, ErrorAttributeOptions.defaults());
      attributes.put("path", "/api/v1/invitations");
      return attributes;
    }
    return super.getErrorAttributes(request, options);
  }
}
