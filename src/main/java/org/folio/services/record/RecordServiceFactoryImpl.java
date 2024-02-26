package org.folio.services.record;

import java.util.List;
import java.util.Map;

import org.folio.service.RecordService;
import org.folio.service.RecordServiceImpl;
import org.folio.service.spi.RecordServiceFactory;

import io.vertx.core.Vertx;

public class RecordServiceFactoryImpl implements RecordServiceFactory {

  @Override
  public RecordService create(Vertx vertx) {
    return RecordServiceImpl.createForMultipleTables(
      vertx,
      Map.of(
        "po_line",
        List.of("po_line", "order_templates"),
        "purchase_order",
        List.of("purchase_order", "order_templates")));
  }
}
