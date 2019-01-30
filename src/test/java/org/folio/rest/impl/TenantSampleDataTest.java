package org.folio.rest.impl;

import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class TenantSampleDataTest extends OrdersStorageTest{

  @Test
  public void isTenantCreated()
  {
    getData(TENANT_ENDPOINT).
    then().log().ifValidationFails()
    .statusCode(200);

  }

}
