package dev.hata.scheduler.dealership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.common.config.RateLimitProperties;
import dev.hata.scheduler.dealership.model.Dealership;
import dev.hata.scheduler.dealership.repository.DealershipRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DealershipServiceTest {

    private static final UUID DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private DealershipRepository repository;

    private RateLimitProperties properties;
    private DealershipService service;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties(null, false);
        service = new DealershipService(repository, properties);
    }

    @Test
    void resolveReturnsDefaultsWhenDealershipIdIsNull() {
        RateLimitProperties.Resolved limits = service.resolveRateLimits(null);

        assertThat(limits.otpSendPerPhonePurpose()).isEqualTo(5);
        assertThat(limits.otpSendPerIp()).isEqualTo(50);
    }

    @Test
    void resolveReturnsDefaultsWhenDealershipNotFound() {
        when(repository.findById(DEALERSHIP_ID)).thenReturn(Optional.empty());

        RateLimitProperties.Resolved limits = service.resolveRateLimits(DEALERSHIP_ID);

        assertThat(limits.otpSendPerPhonePurpose()).isEqualTo(5);
    }

    @Test
    void resolveParsesCustomFeaturesJson() {
        Dealership dealership = new Dealership();
        dealership.setFeatures("{\"rateLimits\":{\"otpSendPerPhonePurpose\":7,\"otpSendPerIp\":60}}");
        when(repository.findById(DEALERSHIP_ID)).thenReturn(Optional.of(dealership));

        RateLimitProperties.Resolved limits = service.resolveRateLimits(DEALERSHIP_ID);

        assertThat(limits.otpSendPerPhonePurpose()).isEqualTo(7);
        assertThat(limits.otpSendPerIp()).isEqualTo(60);
        assertThat(limits.otpVerifyPerIp()).isEqualTo(50); // default fallback
    }

    @Test
    void resolveFallsBackToDefaultsOnInvalidJson() {
        Dealership dealership = new Dealership();
        dealership.setFeatures("invalid-json");
        when(repository.findById(DEALERSHIP_ID)).thenReturn(Optional.of(dealership));

        RateLimitProperties.Resolved limits = service.resolveRateLimits(DEALERSHIP_ID);

        assertThat(limits.otpSendPerPhonePurpose()).isEqualTo(5);
    }

    @Test
    void resolveReturnsDefaultsWhenFeaturesNullOrBlank() {
        Dealership dealership = new Dealership();
        dealership.setFeatures(null);
        when(repository.findById(DEALERSHIP_ID)).thenReturn(Optional.of(dealership));

        assertThat(service.resolveRateLimits(DEALERSHIP_ID).otpSendPerPhonePurpose())
                .isEqualTo(5);

        dealership.setFeatures("   ");
        assertThat(service.resolveRateLimits(DEALERSHIP_ID).otpSendPerPhonePurpose())
                .isEqualTo(5);
    }

    @Test
    void resolveReturnsDefaultsWhenNoRateLimitsKey() {
        Dealership dealership = new Dealership();
        dealership.setFeatures("{\"otherFeature\":true}");
        when(repository.findById(DEALERSHIP_ID)).thenReturn(Optional.of(dealership));

        assertThat(service.resolveRateLimits(DEALERSHIP_ID).otpSendPerPhonePurpose())
                .isEqualTo(5);
    }

    @Test
    void resolveReturnsDefaultsWhenRateLimitsNotAMap() {
        Dealership dealership = new Dealership();
        dealership.setFeatures("{\"rateLimits\":\"not-a-map\"}");
        when(repository.findById(DEALERSHIP_ID)).thenReturn(Optional.of(dealership));

        assertThat(service.resolveRateLimits(DEALERSHIP_ID).otpSendPerPhonePurpose())
                .isEqualTo(5);
    }

    @Test
    void resolveUsesTestLocationWhenConfigured() {
        RateLimitProperties testProps = new RateLimitProperties("classpath:config/rate-limits-test.json", false);
        DealershipService testService = new DealershipService(repository, testProps);

        RateLimitProperties.Resolved limitsWithDealer = testService.resolveRateLimits(DEALERSHIP_ID);
        assertThat(limitsWithDealer).isNotNull();

        RateLimitProperties.Resolved limitsWithoutDealer = testService.resolveRateLimits(null);
        assertThat(limitsWithoutDealer).isNotNull();
    }
}
