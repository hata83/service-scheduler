package dev.hata.scheduler.scheduling.service;

/**
 * Thrown when a slot availability request specifies a date that exceeds
 * the dealership maximum advance booking window (SYSTEM_DESIGN §5.4 Step 1).
 *
 * <p>The controller maps this to HTTP 400 Problem Details.
 */
public class SlotDateOutOfRangeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SlotDateOutOfRangeException(String message) {
        super(message);
    }
}
