package org.folio.rest.persist;

import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;

public class QueryHolder {

  private String table;
  private String searchField;
  private String query;
  private int offset;
  private int limit;

  public QueryHolder(String table, String searchField, String query, int offset, int limit) {
    this.table = table;
    this.searchField = searchField;
    this.query = query;
    this.offset = offset;
    this.limit = limit;
  }

  public QueryHolder(String table, String query, int offset, int limit) {
    this(table, HelperUtils.JSONB, query, offset, limit);
  }

  public String getTable() {

    return table;
  }

  public String getSearchField() {
    return searchField;
  }

  public String getQuery() {
    return query;
  }

  public int getOffset() {
    return offset;
  }

  public int getLimit() {
    return limit;
  }

  public CQLWrapper buildCQLQuery() throws FieldException {
    CQL2PgJSON cql2PgJSON = new CQL2PgJSON(String.format("%s.%s", table, searchField));
    return new CQLWrapper(cql2PgJSON, query)
      .setLimit(new Limit(limit))
      .setOffset(new Offset(offset));
  }
}
