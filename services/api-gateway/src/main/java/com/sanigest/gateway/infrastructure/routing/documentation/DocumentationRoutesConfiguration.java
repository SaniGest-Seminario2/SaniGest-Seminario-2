package com.sanigest.gateway.infrastructure.routing.documentation;

import static com.sanigest.gateway.infrastructure.routing.RouteDestination.destination;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;

@Configuration(proxyBeanMethods = false)
public class DocumentationRoutesConfiguration {
  public static final List<String> DOCUMENTED_MODULES =
      List.of("identity", "company", "catalog", "inventory", "sales", "expense", "finance");

  @Bean
  @Order(0)
  @ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
  RouteLocator documentationRoutes(RouteLocatorBuilder builder, Environment environment) {
    var routes = builder.routes();
    for (String module : DOCUMENTED_MODULES) {
      String property = module.toUpperCase(Locale.ROOT) + "_SERVICE_URL";
      if (environment.containsProperty(property)) {
        routes.route(
            module + "-openapi",
            route ->
                route
                    .path("/v3/api-docs/" + module)
                    .and()
                    .method(HttpMethod.GET)
                    .filters(filters -> filters.setPath("/v3/api-docs"))
                    .uri(destination(environment, property)));
      }
    }
    return routes.build();
  }
}
