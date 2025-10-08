package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tokens")
@Data
@NoArgsConstructor
public class Tokens {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "token", columnDefinition = "TEXT")
    private String token;
    
    public Tokens(String token) {
        this.token = token;
    }
}
