package com.sanigest.sales.infrastructure.integration.catalog;

import com.sanigest.sales.application.exception.CatalogAccessDeniedException;
import com.sanigest.sales.application.exception.CatalogResourceInvalidException;
import com.sanigest.sales.application.exception.CatalogUnavailableException;
import com.sanigest.sales.application.exception.InvoiceStockUnavailable;
import com.sanigest.sales.application.port.out.integration.CatalogClient;
import com.sanigest.sales.application.port.out.integration.InvoiceStock;
import io.micrometer.observation.ObservationRegistry;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CatalogClientProperties.class)
class CatalogClientConfiguration {
  @Bean
  CatalogClient catalogClient(
      CatalogClientProperties properties,
      Environment environment,
      ObjectProvider<ObservationRegistry> observations) {
    properties.validateOrigin(environment.matchesProfiles("local", "test"));
    var api =
        Optional.ofNullable(properties.origin())
            .map(origin ->
                createApi(
                    properties,
                    origin,
                    observations.getIfAvailable(() -> ObservationRegistry.NOOP),
                    CatalogHttpApi.class));
    return new CatalogHttpClient(api);
  }

  @Bean
  InvoiceStock invoiceStock(
      @Value("${sanigest.inventory.origin:}") URI inventoryOrigin,
      CatalogClientProperties timeouts,
      Environment environment,
      ObjectProvider<ObservationRegistry> observations) {
    var properties =
        new CatalogClientProperties(
            inventoryOrigin, timeouts.connectTimeout(), timeouts.responseTimeout());
    properties.validateOrigin(environment.matchesProfiles("local", "test"));
    var api =
        Optional.ofNullable(properties.origin())
            .map(origin ->
                createApi(
                    properties,
                    origin,
                    observations.getIfAvailable(() -> ObservationRegistry.NOOP),
                    InventoryStockHttpApi.class));
    return (item, company) -> {
      var client = api.orElseThrow(InvoiceStockUnavailable::new);
      InventoryStockHttpApi.StockPage page;
      try {
        page = client.tracked(company, item, true, 1);
      } catch (RestClientException
          | CatalogAccessDeniedException
          | CatalogResourceInvalidException
          | CatalogUnavailableException unavailable) {
        throw new InvoiceStockUnavailable();
      }
      if (page == null || page.totalElements() == null || page.totalElements() < 0) {
        throw new InvoiceStockUnavailable();
      }
      return page.totalElements() > 0;
    };
  }

  private static <T> T createApi(
      CatalogClientProperties properties,
      URI origin,
      ObservationRegistry observations,
      Class<T> apiType) {
    var http =
        HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .proxy(ProxySelector.of(null))
            .build();
    var transport = new JdkClientHttpRequestFactory(http);
    transport.setReadTimeout(properties.responseTimeout());

    var restClient =
        RestClient.builder()
            .baseUrl(origin)
            .requestFactory(transport)
            .requestInterceptor(new CatalogIdentityInterceptor(origin))
            .observationRegistry(observations)
            .configureMessageConverters(converters ->
                converters.withJsonConverter(new JacksonJsonHttpMessageConverter(catalogJson())))
            .defaultStatusHandler(
                status -> status.value() != 200,
                (request, response) -> {
                  throw CatalogHttpErrors.forStatus(response.getStatusCode().value());
                })
            .build();
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(apiType);
  }

  private static JsonMapper catalogJson() {
    return JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .withCoercionConfig(
            LogicalType.Textual,
            config ->
                config
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
        .build();
  }
}
