package com.sanigest.gateway.routing;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.URI_TEMPLATE_VARIABLES_ATTRIBUTE;

import com.sanigest.gateway.observability.TraceHeaders;
import com.sanigest.security.CompanySelector;
import com.sanigest.security.CompanySelector.InvalidContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer.PathSegment;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
class CompanyContextFilter implements GlobalFilter, Ordered {
  private static final Set<String> BUSINESS_ROUTES = Set.of("catalog", "sales");
  private static final List<String> INTERNAL_HEADERS =
      List.of(
          "X-Company-User-Assertion",
          "X-Catalog-Company-Id",
          "X-Account-Type",
          "X-User-Id",
          "X-User-Role",
          "X-User-Roles",
          "X-User-Permissions",
          "X-Company-Role",
          "X-Company-Permissions",
          "X-Verified-Company-Id",
          "X-Authenticated-User-Id");

  @Override
  public int getOrder() {
    return -10;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (exchange.getRequest().getPath().elements().stream()
        .anyMatch(element ->
            element instanceof PathSegment segment && invalidSegment(segment.valueToMatch()))) {
      exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
      return exchange.getResponse().setComplete();
    }
    Route route = exchange.getRequiredAttribute(GATEWAY_ROUTE_ATTR);
    Map<String, String> variables =
        exchange.getAttributeOrDefault(URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of());
    String pathCompany = variables.get("companyId");
    if ("me".equals(pathCompany)) {
      pathCompany = null;
    }
    UUID company;
    try {
      company =
          CompanySelector.resolve(
              exchange.getRequest().getHeaders().getOrEmpty(CompanySelector.HEADER),
              pathCompany,
              BUSINESS_ROUTES.contains(route.getId()));
    } catch (InvalidContext invalid) {
      return problem(exchange, invalid.getMessage());
    }
    var request =
        exchange
            .getRequest()
            .mutate()
            .headers(headers -> {
              INTERNAL_HEADERS.forEach(headers::remove);
              headers.remove(CompanySelector.HEADER);
              if (company != null) {
                headers.set(CompanySelector.HEADER, company.toString());
              }
            })
            .build();
    return chain.filter(exchange.mutate().request(request).build());
  }

  private static boolean invalidSegment(String value) {
    return ".".equals(value)
        || "..".equals(value)
        || value.indexOf('/') >= 0
        || value.indexOf('\\') >= 0;
  }

  private Mono<Void> problem(ServerWebExchange exchange, String code) {
    var response = exchange.getResponse();
    response.setStatusCode(HttpStatus.BAD_REQUEST);
    response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    String traceId = exchange.getRequest().getHeaders().getFirst(TraceHeaders.TRACE_ID);
    byte[] body =
        """
        {"type":"about:blank","title":"Bad Request","status":400,"errorCode":"%s","timestamp":"%s","traceId":"%s"}
        """
            .formatted(code, Instant.now(), traceId)
            .getBytes(StandardCharsets.UTF_8);
    return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
  }
}
