package com.sanigest.sales.infrastructure.integration.catalog;

import com.sanigest.companyaccess.AuthenticatedActor;
import com.sanigest.companyaccess.TraceIds;
import com.sanigest.sales.application.exception.CatalogAccessDeniedException;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

final class CatalogIdentityInterceptor implements ClientHttpRequestInterceptor {
  private final URI origin;

  CatalogIdentityInterceptor(URI origin) {
    this.origin = origin;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    var destination = request.getURI();
    if (request.getMethod() != HttpMethod.GET
        || !origin.getScheme().equalsIgnoreCase(destination.getScheme())
        || !origin.getHost().equalsIgnoreCase(destination.getHost())
        || effectivePort(origin) != effectivePort(destination)
        || destination.getUserInfo() != null) {
      throw new IllegalStateException("Invalid Catalog request destination");
    }
    if (AuthenticatedActor.userId().isEmpty()
        || !(SecurityContextHolder.getContext().getAuthentication()
            instanceof JwtAuthenticationToken authentication)) {
      throw new CatalogAccessDeniedException(CatalogAccessDeniedException.Reason.MISSING_IDENTITY);
    }
    var headers = request.getHeaders();
    headers.setBearerAuth(authentication.getToken().getTokenValue());
    var trace = TraceIds.normalize(MDC.get("traceId"));
    headers.set("X-Trace-Id", trace);
    headers.set("traceparent", "00-" + trace + "-" + spanId() + "-00");
    return execution.execute(request, body);
  }

  private static int effectivePort(URI uri) {
    return uri.getPort() >= 0
        ? uri.getPort()
        : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
  }

  private static String spanId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
  }
}
