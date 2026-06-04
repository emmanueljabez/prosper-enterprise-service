package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.CompanySignupIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanySignupIntentRepository extends JpaRepository<CompanySignupIntent, UUID> {

    Optional<CompanySignupIntent> findByToken(String token);
}
