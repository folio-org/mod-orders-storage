package org.folio.dao;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.persist.Conn;

public class ExtensionRepository {
  private static final Logger log = LogManager.getLogger();
  private static final String UUID_OSSP_EXTENSION = "uuid-ossp";
  private static final String CREATE_EXTENSION_STATEMENT = """
    CREATE EXTENSION IF NOT EXISTS "%s" WITH SCHEMA public;
    """;

  public Future<Void> createUUIDExtension(Conn conn) {
    return createExtension(UUID_OSSP_EXTENSION, conn);
  }

  private Future<Void> createExtension(String extensionName, Conn conn) {
    String query = String.format(CREATE_EXTENSION_STATEMENT, extensionName);
    return conn.execute(query)
      .onSuccess(result -> log.info("createExtension:: Extension created or existed, extensionName={}", extensionName))
      .onFailure(t -> log.error("createExtension:: Failed to create extension, extensionName={}", extensionName, t))
      .mapEmpty();
  }

}
