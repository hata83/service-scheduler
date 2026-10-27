package dev.hata.scheduler.scheduling.service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.scheduling.dto.SlotAvailabilityResponse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Domain service for retrieving cached slot availability grids.
 */
@Service
public class SlotAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(SlotAvailabilityService.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public SlotAvailabilityService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * Reads candidate available time slots from Redis cache key grid.
     */
    public List<SlotAvailabilityResponse> getCachedAvailableSlots(
            UUID dealershipId, UUID serviceTypeId, LocalDate date) {
        String key = "slot:grid:" + dealershipId + ":" + serviceTypeId + ":" + date;
        try {
            String json = redis.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            return objectMapper.readValue(json, new TypeReference<List<SlotAvailabilityResponse>>() {});
        } catch (Exception ex) {
            log.warn("Failed to read or deserialize slot grid for key {}: {}", key, ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }
}
