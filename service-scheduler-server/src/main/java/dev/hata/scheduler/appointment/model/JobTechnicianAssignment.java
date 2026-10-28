package dev.hata.scheduler.appointment.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Technician assignment to a service job (SYSTEM_DESIGN §4.2.13).
 */
@Entity
@Table(name = "job_technician_assignments")
public class JobTechnicianAssignment {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "technician_id", nullable = false)
    private UUID technicianId;

    @Column(name = "role", nullable = false, length = 50)
    private String role;

    @Column(name = "assigned_start_time", nullable = false)
    private Instant assignedStartTime;

    @Column(name = "assigned_end_time", nullable = false)
    private Instant assignedEndTime;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public JobTechnicianAssignment() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public UUID getTechnicianId() {
        return technicianId;
    }

    public void setTechnicianId(UUID technicianId) {
        this.technicianId = technicianId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Instant getAssignedStartTime() {
        return assignedStartTime;
    }

    public void setAssignedStartTime(Instant assignedStartTime) {
        this.assignedStartTime = assignedStartTime;
    }

    public Instant getAssignedEndTime() {
        return assignedEndTime;
    }

    public void setAssignedEndTime(Instant assignedEndTime) {
        this.assignedEndTime = assignedEndTime;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }
}
