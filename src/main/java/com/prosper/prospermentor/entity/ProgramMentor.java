package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * ProgramMentor entity representing the many-to-many relationship between programs and mentor profiles
 * Maps to the program_mentors table in Supabase
 * Links Program entities to Profile entities (where role is MENTOR)
 */
@Entity
@Table(name = "program_mentors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@IdClass(ProgramMentor.ProgramMentorId.class)
public class ProgramMentor {

    @Id
    @Column(name = "program_id", nullable = false)
    private UUID programId;

    @Id
    @Column(name = "mentor_id", nullable = false)
    private UUID mentorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", insertable = false, updatable = false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_id", insertable = false, updatable = false)
    private Profile mentor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Composite primary key class for ProgramMentor
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProgramMentorId implements Serializable {
        private UUID programId;
        private UUID mentorId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProgramMentorId that = (ProgramMentorId) o;
            return Objects.equals(programId, that.programId) &&
                   Objects.equals(mentorId, that.mentorId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(programId, mentorId);
        }
    }
}