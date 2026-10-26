package dev.hata.scheduler.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.hata.scheduler.common.ratelimit.RateLimitProperties;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class ClientIpResolverTest {

    @Test
    void resolvesRemoteAddrWhenForwardedForNotTrusted() {
        RateLimitProperties props = mock(RateLimitProperties.class);
        when(props.trustForwardedFor()).thenReturn(false);

        ClientIpResolver resolver = new ClientIpResolver(props);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("192.168.1.50");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.195");

        assertThat(resolver.resolve(request)).isEqualTo("192.168.1.50");
    }

    @Test
    void resolvesFirstForwardedForWhenTrusted() {
        RateLimitProperties props = mock(RateLimitProperties.class);
        when(props.trustForwardedFor()).thenReturn(true);

        ClientIpResolver resolver = new ClientIpResolver(props);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.195, 10.0.0.1");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.195");
    }

    @Test
    void fallsBackToRemoteAddrWhenForwardedForHeaderBlank() {
        RateLimitProperties props = mock(RateLimitProperties.class);
        when(props.trustForwardedFor()).thenReturn(true);

        ClientIpResolver resolver = new ClientIpResolver(props);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("   ");
        when(request.getRemoteAddr()).thenReturn("192.168.1.50");

        assertThat(resolver.resolve(request)).isEqualTo("192.168.1.50");
    }
}
