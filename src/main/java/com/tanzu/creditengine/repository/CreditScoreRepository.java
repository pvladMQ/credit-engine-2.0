package com.tanzu.creditengine.repository;

import com.tanzu.creditengine.entity.CreditScore;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreditScoreRepository extends JpaRepository<CreditScore, String> {

    /** Authoritative "top N recent" used to warm the cache and as a fallback. */
    List<CreditScore> findAllByOrderByCalculatedAtDesc(Pageable pageable);
}
