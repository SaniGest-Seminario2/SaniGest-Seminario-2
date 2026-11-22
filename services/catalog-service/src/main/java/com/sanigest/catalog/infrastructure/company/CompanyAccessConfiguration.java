package com.sanigest.catalog.infrastructure.company;

import com.sanigest.companyaccess.CompanyAccess;
import com.sanigest.companyaccess.HttpCompanyAccess;
import com.sanigest.companyaccess.HttpCompanyAccess.Settings;
import com.sanigest.security.AccessTokens;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CompanyAccessConfiguration.ClientSettings.class)
@ConditionalOnProperty(name = "sanigest.company-access.enabled", havingValue = "true")
class CompanyAccessConfiguration {
  @ConfigurationProperties("sanigest.company-access")
  record ClientSettings(
      URI origin,
      String issuer,
      String privateKeyLocation,
      @DefaultValue("500ms") Duration connectTimeout,
      @DefaultValue("2s") Duration responseTimeout) {}

  @Bean
  CompanyAccess companyAccess(
      ClientSettings settings,
      @Value("${sanigest.jwt.issuer}") String userIssuer,
      @Value("${sanigest.jwt.public-key}") Resource userKey,
      ResourceLoader resources,
      Environment environment,
      ObjectMapper json,
      ObjectProvider<ObservationRegistry> observations)
      throws IOException {
    var issuer = settings.issuer();
    var privateKeyLocation = settings.privateKeyLocation();
    if (issuer == null
        || issuer.equals(userIssuer)
        || settings.origin() == null
        || privateKeyLocation == null
        || !privateKeyLocation.startsWith("file:")) {
      throw new IllegalArgumentException("Company access requires dedicated service credentials");
    }
    try (var input = resources.getResource(privateKeyLocation).getInputStream()) {
      var key = RsaKeyConverters.pkcs8().convert(input);
      if (key == null
          || key.getModulus().equals(AccessTokens.verificationKey(userKey).getModulus())) {
        throw new IllegalArgumentException("Company access requires a dedicated RSA key");
      }
      return new HttpCompanyAccess(
          new Settings(
              settings.origin(),
              environment.matchesProfiles("local", "test"),
              CompanyAccess.Module.CATALOG,
              settings.connectTimeout(),
              settings.responseTimeout()),
          issuer,
          key,
          CompanyAccessConfiguration::traceId,
          json,
          observations.getIfAvailable(() -> ObservationRegistry.NOOP));
    }
  }

  @Bean
  CompanyClientCreationAuthorization clientCreationAuthorization(CompanyAccess access) {
    return new CompanyClientCreationAuthorization(access);
  }

  private static String traceId() {
    String trace = MDC.get("traceId");
    if (trace == null
        && RequestContextHolder.getRequestAttributes()
            instanceof ServletRequestAttributes request) {
      return request.getRequest().getHeader("X-Trace-Id");
    }
    return trace;
  }
}
