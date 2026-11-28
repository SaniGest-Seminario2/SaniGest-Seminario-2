package com.sanigest.catalog.api.dto.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sanigest.catalog.domain.exception.client.InvalidClientPatch;
import com.sanigest.catalog.domain.model.client.Client;
import com.sanigest.catalog.domain.model.client.ClientDetails;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class ClientProfileDtoTest {
  private static final JsonMapper JSON = JsonMapper.shared();

  @ParameterizedTest
  @CsvSource({"NATURAL,001-0000001-2,00100000012", "LEGAL,012 345678,012345678"})
  void normalizesNationalDocumentsWithoutRemovingLeadingZeros(
      String type, String document, String digits) {
    var details =
        create(
            """
            {"name":" Cliente ","clientType":"%s","taxId":" %s ","creditLimit":"10.25"}
            """
                .formatted(type, document));
    var profile = details.profile().defaults(UUID.randomUUID());
    var client = Client.register(UUID.randomUUID(), details.withProfile(profile));
    assertThat(client.details().taxId()).isEqualTo(digits);
    assertThat(client.details().name()).isEqualTo("Cliente");
    assertThat(profile.creditLimit()).isEqualTo(new BigDecimal("10.25"));
    assertThat(ClientDetailResponse.from(client).creditLimit()).isEqualTo("10.25");
  }

  @Test
  void preservesForeignAndLegacyDocuments() {
    var foreign =
        create(
            """
            {"name":"Cliente","clientType":"LEGAL","countryCode":"US","taxId":"AB-0012"}
            """);
    assertThat(foreign.withProfile(foreign.profile().defaults(UUID.randomUUID())).taxId())
        .isEqualTo("AB-0012");
    var legacy = new ClientDetails("Cliente", "Ab-012", null, null, null);
    assertThat(legacy.taxId()).isEqualTo("Ab-012");
    assertThat(legacy.profile()).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"name\":\" \"}",
        "{\"name\":\"X\"}",
        "{\"name\":\"X\",\"clientType\":\"PERSON\"}",
        "{\"name\":\"X\",\"alternatePhone\":null}",
        "{\"name\":\"X\",\"clientType\":null}",
        "{\"name\":\"X\",\"clientType\":\"LEGAL\",\"creditLimit\":\"-1\"}",
        "{\"name\":\"X\",\"clientType\":\"LEGAL\",\"creditLimit\":\"1.001\"}",
        "{\"name\":\"X\",\"clientType\":\"LEGAL\",\"creditLimit\":\"100000000000000000\"}",
        "{\"name\":\"X\",\"clientType\":\"LEGAL\",\"creditLimit\":1}",
        "{\"name\":\"X\",\"clientType\":\"LEGAL\",\"email\":\"invalid\"}",
        "{\"name\":\"X\",\"clientType\":\"LEGAL\",\"phone\":\"invalid\"}",
        "{\"name\":\"X\",\"clientType\":\"LEGAL\",\"alternatePhone\":\"invalid\"}",
        "{\"name\":\"X\",\"clientType\":\"LEGAL\",\"municipalityCode\":\"010100\"}",
        "{\"name\":\"X\",\"clientType\":\"LEGAL\",\"paymentTerms\":null}",
        "{\"name\":\"X\",\"clientType\":\"LEGAL\",\"categoryId\":null}",
        "{\"name\":\"X\",\"clientType\":\"LEGAL\",\"countryCode\":null}",
        "{\"name\":\"X\",\"clientType\":\"LEGAL\",\"status\":\"INACTIVE\"}"
      })
  void rejectsInvalidCreationPayloads(String payload) {
    assertThatThrownBy(() -> create(payload)).isInstanceOf(InvalidClientPatch.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "[]",
        "{\"name\":12}",
        "{\"categoryId\":\"1-1-1-1-1\"}",
        "{\"status\":\"INACTIVE\"}",
        "{\"companyId\":null}",
        "{\"availableCredit\":null}"
      })
  void rejectsUnknownOrUntypedPatchFields(String payload) {
    assertThatThrownBy(() -> ClientPatchRequest.details(JSON.readTree(payload)))
        .isInstanceOf(InvalidClientPatch.class);
  }

  @Test
  void validatesResultingProfileAndDistinguishesZeroAndNull() {
    var details =
        create(
            """
            {"name":"X","clientType":"NATURAL","taxId":"00100000012",
             "paymentTerms":"BOTH","alternatePhone":"8095550100","provinceCode":"010000",
             "municipalityCode":"010100"}
            """);
    var client =
        Client.register(
            UUID.randomUUID(), details.withProfile(details.profile().defaults(UUID.randomUUID())));
    var edited =
        client.updateDetails(
            ClientPatchRequest.details(
                JSON.readTree(
                    """
                    {"alternatePhone":null,"creditLimit":"0","paymentTerms":"CREDIT"}
                    """)));
    assertThat(edited.details().profile().alternatePhone()).isNull();
    assertThat(edited.details().profile().creditLimit()).isEqualTo(new BigDecimal("0.00"));
    assertThat(edited.details().taxId()).isEqualTo(details.taxId());
    assertThat(edited.details().profile().paymentTerms().name()).isEqualTo("CREDIT");
    var cleared =
        edited.updateDetails(
            ClientPatchRequest.details(
                JSON.readTree(
                    """
                    {"creditLimit":null,"taxId":null}
                    """)));
    assertThat(ClientDetailResponse.from(cleared).creditLimit()).isNull();
    assertThat(cleared.details().taxId()).isNull();
    for (var invalid :
        new String[] {
          "{\"name\":null}", "{\"categoryId\":null}", "{\"countryCode\":null}",
          "{\"paymentTerms\":null}", "{\"clientType\":null}", "{\"clientType\":\"LEGAL\"}",
          "{\"provinceCode\":null}"
        }) {
      assertThatThrownBy(
              () -> client.updateDetails(ClientPatchRequest.details(JSON.readTree(invalid))))
          .isInstanceOf(InvalidClientPatch.class);
    }
  }

  private static com.sanigest.catalog.domain.model.client.ClientDetails create(String payload) {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      return CreateClientRequest.from(JSON.readTree(payload), factory.getValidator()).details();
    }
  }
}
