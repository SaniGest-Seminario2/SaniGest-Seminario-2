package com.sanigest.template;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.annotation.AnnotationUtils;

class ServiceTemplateApplicationTest {

  @Test
  void declaresAnExecutableSpringBootApplication() {
    SpringBootApplication annotation =
        AnnotationUtils.findAnnotation(
            ServiceTemplateApplication.class, SpringBootApplication.class);

    assertThat(annotation).isNotNull();
  }
}
