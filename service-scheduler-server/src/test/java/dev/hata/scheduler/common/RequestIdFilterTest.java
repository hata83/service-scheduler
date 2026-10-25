package dev.hata.scheduler.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Covers the request id filter (design D12). The filter echoes the request id
 * and stores it in MDC so the JSON logs carry it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequestIdFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    private RequestIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestIdFilter();
    }

    @Test
    void echoesAClientRequestId() throws ServletException, IOException {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest("GET", "/actuator/health");
        httpRequest.addHeader("X-Request-Id", "client-id-1");
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        filter.doFilter(httpRequest, httpResponse, chain);

        assertThat(httpResponse.getHeader("X-Request-Id")).isEqualTo("client-id-1");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void mintsARequestIdWhenTheClientSendsNone() throws ServletException, IOException {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        filter.doFilter(httpRequest, httpResponse, chain);

        assertThat(httpResponse.getHeader("X-Request-Id")).isNotBlank();
    }

    @Test
    void mintsARequestIdForABlankHeader() throws ServletException, IOException {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest("GET", "/actuator/health");
        httpRequest.addHeader("X-Request-Id", "   ");
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        filter.doFilter(httpRequest, httpResponse, chain);

        assertThat(httpResponse.getHeader("X-Request-Id")).isNotBlank();
    }

    @Test
    void mintsARequestIdForAnOverlongHeader() throws ServletException, IOException {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest("GET", "/actuator/health");
        httpRequest.addHeader("X-Request-Id", "x".repeat(65));
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        filter.doFilter(httpRequest, httpResponse, chain);

        assertThat(httpResponse.getHeader("X-Request-Id")).isNotEqualTo("x".repeat(65));
    }
}
