package com.sanigest.company.application.port.out.integration;

import java.util.UUID;

public interface AdvisorEligibility {
  boolean isEligible(UUID advisorId);
}
