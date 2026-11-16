package com.sanigest.template;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServiceTemplateApplicationIT {

  @Autowired private MockMvc mockMvc;

  @Test
  void exposesSafeHealthAndAvailabilityProbes() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components").doesNotExist());
    mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }

  @Test
  void exposesOnlySafeBuildInformation() throws Exception {
    mockMvc
        .perform(get("/actuator/info"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.build.artifact").value("spring-boot-service-template"))
        .andExpect(jsonPath("$.build.version").isNotEmpty())
        .andExpect(content().string(not(containsString("jdbc:"))))
        .andExpect(content().string(not(containsString("password"))))
        .andExpect(content().string(not(containsString("token"))));

    mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    mockMvc.perform(get("/actuator/configprops")).andExpect(status().isNotFound());
  }
}
