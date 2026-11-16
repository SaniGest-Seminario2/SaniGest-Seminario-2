package com.sanigest.company;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.annotation.AnnotationUtils;

class CompanyServiceApplicationTest {

  @Test
  void declaresAnExecutableSpringBootApplication() {
    SpringBootApplication annotation =
        AnnotationUtils.findAnnotation(
            CompanyServiceApplication.class, SpringBootApplication.class);

    assertThat(annotation).isNotNull();
  }
}
