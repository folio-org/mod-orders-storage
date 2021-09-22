package org.folio.config;

import org.folio.dao.lines.PoLinesDAO;
import org.folio.dao.lines.PoLinesPostgresDAO;
import org.folio.migration.MigrationService;
import org.folio.rest.core.RestClient;
import org.folio.services.finance.FinanceService;
import org.folio.services.lines.PoLinesService;
import org.folio.services.order.OrderSequenceRequestBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

  @Bean
  RestClient restClient() {
    return new RestClient();
  }

  @Bean
  PoLinesDAO poLinesDAO() {
    return new PoLinesPostgresDAO();
  }

  @Bean
  PoLinesService poLinesService(PoLinesDAO poLinesDAO) {
    return new PoLinesService(poLinesDAO);
  }

  @Bean
  OrderSequenceRequestBuilder orderSequenceService() {
    return new OrderSequenceRequestBuilder();
  }

  @Bean
  FinanceService financeService(RestClient restClient) {
    return new FinanceService(restClient);
  }

  @Bean
  MigrationService migrationService(FinanceService financeService) {
    return new MigrationService(financeService);
  }
}
