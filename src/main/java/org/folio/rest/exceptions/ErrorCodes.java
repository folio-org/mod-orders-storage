package org.folio.rest.exceptions;

import org.folio.rest.jaxrs.model.Error;

public enum ErrorCodes {

  GENERIC_ERROR_CODE("genericError", "Generic error"),
  POSTGRE_SQL_ERROR("pgException", "PostgreSQL exception"),
  TITLE_EXIST("titleExist", "The title for poLine already exist"),
  ORDER_TEMPLATE_CATEGORY_IS_USED("orderTemplateCategoryIsUsed", "The order template category is used in some order template and cannot be deleted"),
  UNIQUE_FIELD_CONSTRAINT_ERROR("uniqueField{0}{1}Error", "Field {0} must be unique");

  private final String code;
  private final String description;

  ErrorCodes(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public String getCode() {
    return code;
  }

  @Override
  public String toString() {
    return code + ": " + description;
  }

  public Error toError() {
    return new Error().withCode(code).withMessage(description);
  }
}
