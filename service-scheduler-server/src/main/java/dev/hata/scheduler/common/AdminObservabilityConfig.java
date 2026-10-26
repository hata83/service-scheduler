package dev.hata.scheduler.common;

import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Observability configuration for HTTP exchange tracing and actuator monitoring (design D12).
 * Enables the in-memory exchange repository powering the Admin UI tracing dashboard.
 */
@Configuration
public class AdminObservabilityConfig {

    @Bean
    public HttpExchangeRepository httpExchangeRepository() {
        InMemoryHttpExchangeRepository repository = new InMemoryHttpExchangeRepository();
        repository.setCapacity(100);
        return repository;
    }
}
