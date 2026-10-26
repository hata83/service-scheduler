package dev.hata.scheduler.resource.model;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for {@link TechnicianSkill}.
 */
public record TechnicianSkillId(UUID technicianId, TechnicianSkillCode skill) implements Serializable {}
