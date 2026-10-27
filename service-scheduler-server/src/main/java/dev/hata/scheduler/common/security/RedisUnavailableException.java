package dev.hata.scheduler.common.security;

/**
 * Thrown when Redis is unavailable during session validation or account state checks.
 * Represents a fail-closed outage path where authentication cannot proceed.
 */
public class RedisUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RedisUnavailableException(String message) {
        super(message);
    }

    public RedisUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
