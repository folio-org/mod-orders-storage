package org.folio.dao.lines;

import java.util.List;

import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;

public interface PoLinesDAO {

  Future<List<PoLine>> getPoLines(Criterion criterion, Conn conn);
  Future<PoLine> getPoLineById(String id, DBClient client);
  Future<Integer> updatePoLines(String sql, Conn conn);

}
