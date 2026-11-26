package com.sanigest.company.api.dto;

import static com.sanigest.company.api.dto.ValueOption.of;

import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.CompanyStatus;
import java.util.List;

public record CompanyOptions(
    List<ValueOption<String>> invitationRoles,
    List<ValueOption<String>> companyStatuses,
    List<ValueOption<String>> companyStatusFilters) {
  public static CompanyOptions from(List<CompanyRole> roles) {
    var active = of(CompanyStatus.ACTIVE, "Activa");
    var inactive = of(CompanyStatus.INACTIVE, "Inactiva");
    return new CompanyOptions(
        roles.stream()
            .map(role ->
                of(
                    role,
                    switch (role) {
                      case OWNER -> "Propietario";
                      case ADMIN -> "Administrador";
                      case OPERATOR -> "Operador";
                      case VIEWER -> "Lector";
                    }))
            .toList(),
        List.of(active, inactive),
        List.of(new ValueOption<>("ALL", "Todos"), active, inactive));
  }
}
