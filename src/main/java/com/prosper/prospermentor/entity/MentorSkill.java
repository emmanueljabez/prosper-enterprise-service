package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * MentorSkill entity representing the many-to-many relationship between mentors and skills
 * Maps to the mentor_skills table in Supabase
 */
@Entity
@Table(name = "mentor_skills")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@IdClass(MentorSkill.MentorSkillId.class)
public class MentorSkill {

    /**
     * Mentor ID - foreign key to mentor_profiles
     */
    @Id
    @Column(name = "mentor_id", nullable = false)
    private UUID mentorId;

    /**
     * Skill ID - foreign key to skills
     */
    @Id
    @Column(name = "skill_id", nullable = false)
    private UUID skillId;

    /**
     * Reference to mentor profile
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_id", insertable = false, updatable = false)
    private MentorProfile mentor;

    /**
     * Reference to skill
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", insertable = false, updatable = false)
    private Skill skill;

    /**
     * Constructor with IDs
     */
    public MentorSkill(UUID mentorId, UUID skillId) {
        this.mentorId = mentorId;
        this.skillId = skillId;
    }

    /**
     * Composite primary key class for MentorSkill
     */
    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MentorSkillId implements Serializable {
        
        private UUID mentorId;
        private UUID skillId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MentorSkillId that = (MentorSkillId) o;
            return Objects.equals(mentorId, that.mentorId) && 
                   Objects.equals(skillId, that.skillId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mentorId, skillId);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MentorSkill that = (MentorSkill) o;
        return Objects.equals(mentorId, that.mentorId) && 
               Objects.equals(skillId, that.skillId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mentorId, skillId);
    }
}


