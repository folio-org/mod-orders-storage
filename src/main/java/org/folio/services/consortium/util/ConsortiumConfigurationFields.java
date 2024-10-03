package org.folio.services.consortium.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ConsortiumConfigurationFields {

  CONSORTIUM_ID("consortiumId"),
  CENTRAL_TENANT_ID("centralTenantId"),
  USER_TENANTS("userTenants");

  private final String value;

}
