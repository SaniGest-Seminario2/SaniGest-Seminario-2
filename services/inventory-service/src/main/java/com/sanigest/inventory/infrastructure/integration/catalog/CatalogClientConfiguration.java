package com.sanigest.inventory.infrastructure.integration.catalog;

import com.sanigest.inventory.application.port.out.integration.CatalogInventoryClient;
import io.micrometer.observation.ObservationRegistry;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
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
  CatalogInventoryClient catalogClient(
      CatalogClientProperties properties,
      Environment environment,
      ObjectProvider<ObservationRegistry> observations) {
    properties.validateOrigin(environment.matchesProfiles("local", "test"));
    if (properties.origin() == null) {
      throw new IllegalArgumentException("Catalog origin is required");
    }
    return new CatalogHttpClient(
        createApi(
            properties,
            properties.origin(),
            observations.getIfAvailable(() -> ObservationRegistry.NOOP)));
  }

  private static CatalogHttpApi createApi(
      CatalogClientProperties properties, URI origin, ObservationRegistry observations) {
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
            .observationRegistry(observations)
            .configureMessageConverters(converters ->
                converters.withJsonConverter(new JacksonJsonHttpMessageConverter(catalogJson())))
            .build();
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(CatalogHttpApi.class);
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
