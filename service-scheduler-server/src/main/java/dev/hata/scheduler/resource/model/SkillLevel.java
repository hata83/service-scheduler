package dev.hata.scheduler.resource.model;

/**
 * Per-domain skill proficiency taxonomy in {@code technician_skills.level}
 * (SYSTEM_DESIGN §4.2.4).
 */
public enum SkillLevel {
    TRAINEE(1),
    JUNIOR(2),
    TECHNICIAN(3),
    SENIOR(4),
    MASTER(5);

    private final int rank;

    SkillLevel(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    /**
     * Reports whether this level meets or exceeds the required minimum level.
     */
    public boolean satisfies(SkillLevel minimum) {
        return this.rank >= minimum.rank;
    }
}
