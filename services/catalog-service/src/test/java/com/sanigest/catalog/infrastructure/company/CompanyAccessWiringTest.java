package com.sanigest.catalog.infrastructure.company;

import static org.assertj.core.api.Assertions.assertThat;

import com.sanigest.catalog.application.ClientCreationAuthorization;
import com.sanigest.companyaccess.CompanyAccess;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

class CompanyAccessWiringTest {
  @TempDir Path keys;

  @Test
  void enablesTheAdapterWithDedicatedCredentialsAndValidDefaultTimeouts() throws Exception {
    KeyPair user = generateKey();
    KeyPair service = generateKey();
    var userKey = pem("user.pem", "PUBLIC KEY", user.getPublic().getEncoded());
    var serviceKey = pem("service.pem", "PRIVATE KEY", service.getPrivate().getEncoded());

    runner()
        .withPropertyValues(
            "sanigest.company-access.enabled=true",
            "sanigest.company-access.origin=https://company.internal",
            "sanigest.company-access.issuer=catalog-internal",
            "sanigest.company-access.private-key-location=" + serviceKey.toUri(),
            "sanigest.jwt.issuer=user-sessions",
            "sanigest.jwt.public-key=" + userKey.toUri())
        .run(context -> {
          assertThat(context).hasSingleBean(CompanyAccess.class);
          assertThat(context).hasSingleBean(ClientCreationAuthorization.class);
        });
  }

  @Test
  void disabledByDefaultAndFailsStartupForMissingCredentialsWhenEnabled() {
    runner().run(context -> assertThat(context).doesNotHaveBean(ClientCreationAuthorization.class));
    runner()
        .withPropertyValues("sanigest.company-access.enabled=true")
        .run(context -> assertThat(context).hasFailed());
  }

  private static ApplicationContextRunner runner() {
    return new ApplicationContextRunner()
        .withUserConfiguration(CompanyAccessConfiguration.class)
        .withBean(ObjectMapper.class, () -> new ObjectMapper());
  }

  private Path pem(String file, String type, byte[] encoded) throws Exception {
    return Files.writeString(
        keys.resolve(file),
        "-----BEGIN "
            + type
            + "-----\n"
            + Base64.getMimeEncoder().encodeToString(encoded)
            + "\n-----END "
            + type
            + "-----\n");
  }

  private static KeyPair generateKey() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }
}
