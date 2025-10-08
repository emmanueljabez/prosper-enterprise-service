package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.Tokens;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TokenRepositories extends JpaRepository<Tokens, Long> {
}
