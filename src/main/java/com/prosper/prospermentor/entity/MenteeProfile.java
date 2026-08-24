package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * MenteeProfile entity representing mentee profile information
 * Maps to the mentee_profiles table in Supabase
 */
@Entity
@Table(name = "mentee_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenteeProfile {

    /**
     * Primary key - UUID that also serves as foreign key to profiles table
     */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /**
     * Career level of the mentee
     */
    @Column(name = "career_level")
    private String careerLevel;

    /**
     * Industry the mentee works in
     */
    @Column(name = "industry")
    private String industry;

    /**
     * List of goals the mentee wants to achieve
     * Stored as text array in PostgreSQL
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "goals", columnDefinition = "text[]")
    private String[] goals;

    /**
     * List of interests the mentee has
     * Stored as text array in PostgreSQL
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "interests", columnDefinition = "text[]")
    private String[] interests;

    /**
     * Preferred learning style
     */
    @Column(name = "learning_style")
    private String learningStyle;

    /**
     * Preferred session duration in minutes
     */
    @Column(name = "preferred_session_duration")
    private Integer preferredSessionDuration = 60;

    /**
     * Budget range for mentoring sessions
     */
    @Column(name = "budget_range")
    private String budgetRange;

    /**
     * Detailed sub-goals stored as JSONB
     */
    @Column(name = "sub_goals", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String subGoals = "{}";

    /**
     * Notes about goals stored as JSONB
     */
    @Column(name = "goal_notes", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String goalNotes = "{}";

    /**
     * Audit fields
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public List<String> getGoals() {
        return TextArrayMapping.toList(goals);
    }

    public void setGoals(List<String> goals) {
        this.goals = TextArrayMapping.fromList(goals);
    }

    public void setGoals(String[] goals) {
        this.goals = goals;
    }

    public List<String> getInterests() {
        return TextArrayMapping.toList(interests);
    }

    public void setInterests(List<String> interests) {
        this.interests = TextArrayMapping.fromList(interests);
    }

    public void setInterests(String[] interests) {
        this.interests = interests;
    }
}
