package com.sanigest.company.api.error.invitation;

import com.sanigest.company.api.routing.CompanyRoutes;
import jakarta.servlet.RequestDispatcher;
import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;

/** Removes the acceptance URL from fallback error responses. */
@Component
class InvitationErrorAttributes extends DefaultErrorAttributes {
  @Override
  public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
    Object path =
        request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI, RequestAttributes.SCOPE_REQUEST);
    if (path instanceof String uri && CompanyRoutes.isInvitationPath(uri)) {
      var attributes = super.getErrorAttributes(request, ErrorAttributeOptions.defaults());
      attributes.put("path", "/api/v1/invitations");
      return attributes;
    }
    return super.getErrorAttributes(request, options);
  }
}
