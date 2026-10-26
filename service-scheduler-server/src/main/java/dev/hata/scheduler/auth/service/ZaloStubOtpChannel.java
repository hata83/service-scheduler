package dev.hata.scheduler.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Phase 1 OTP channel. The stub prints the code to the server console so the
 * Bruno and Playwright suites can read it (design D7).
 */
@Component
@ConditionalOnProperty(name = "app.auth.otp.channel", havingValue = "zalo-stub", matchIfMissing = true)
public class ZaloStubOtpChannel implements OtpChannel {

    private static final Logger log = LoggerFactory.getLogger(ZaloStubOtpChannel.class);

    @Override
    public void send(String phone, String code) {
        log.info("ZALO_OTP_STUB phone={} code={}", phone, code);
    }
}
