package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.postTenant;
import static org.hamcrest.Matchers.is;

import io.restassured.http.Header;
import io.restassured.http.Headers;
import lombok.SneakyThrows;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.utils.TenantApiTestUtil;
import org.junit.jupiter.api.Test;

public class TenantReferenceDataTest extends TestBase {
  private static final Header REF_HEADER = new Header(OKAPI_HEADER_TENANT, "ref");

  @Test
  @SneakyThrows
  public void referenceData() {
    var url = "/orders-storage/acquisition-methods/{id}";
    TenantAttributes tenantAttributes = TenantApiTestUtil.prepareTenantBody(false, true);
    postTenant(REF_HEADER, tenantAttributes);
    deleteData(url, "da6703b1-81fe-44af-927a-94f24d1ab8ee", REF_HEADER)
        .then().statusCode(204);
    // translate English "Gift" to German "Geschenk"
    putData(url, "0a4163a5-d225-4007-ad90-2fb41b73efab",
        """
        {
          "id": "0a4163a5-d225-4007-ad90-2fb41b73efab",
          "value": "Geschenk",
          "source": "System"
        }
        """, Headers.headers(REF_HEADER))
        .then().statusCode(204);

    // migrate from Orchid (13.5.0), the two acquisition methods exist in that version
    // and therefore should not be reinstalled when migrating to any later version
    tenantAttributes = tenantAttributes.withModuleFrom("13.5.0");
    postTenant(REF_HEADER, tenantAttributes);

    getDataById(url, "da6703b1-81fe-44af-927a-94f24d1ab8ee", REF_HEADER)
        .then().statusCode(404);
    getDataById(url, "0a4163a5-d225-4007-ad90-2fb41b73efab", REF_HEADER)
        .then().statusCode(200).body("value", is("Geschenk"));
  }
}
