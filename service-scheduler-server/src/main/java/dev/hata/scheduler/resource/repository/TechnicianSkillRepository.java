package dev.hata.scheduler.resource.repository;

import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.resource.model.TechnicianSkill;
import dev.hata.scheduler.resource.model.TechnicianSkillId;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes the {@code technician_skills} table.
 */
public interface TechnicianSkillRepository extends JpaRepository<TechnicianSkill, TechnicianSkillId> {

    List<TechnicianSkill> findByTechnicianId(UUID technicianId);
}
