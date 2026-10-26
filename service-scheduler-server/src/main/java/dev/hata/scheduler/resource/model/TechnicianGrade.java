package dev.hata.scheduler.resource.model;

/**
 * Overall technician career grade in {@code technicians.grade}
 * (SYSTEM_DESIGN §4.2.3). Used when ranking and assigning technicians to
 * multi-technician service jobs.
 */
public enum TechnicianGrade {
    TRAINEE(1),
    JUNIOR(2),
    TECHNICIAN(3),
    SENIOR(4),
    MASTER(5);

    private final int rank;

    TechnicianGrade(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    /**
     * Reports whether this grade meets or exceeds the required minimum grade.
     */
    public boolean satisfies(TechnicianGrade minimum) {
        return this.rank >= minimum.rank;
    }
}
