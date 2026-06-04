package com.tanzu.creditengine.repository;

import com.tanzu.creditengine.entity.CreditHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreditHistoryRepository extends JpaRepository<CreditHistory, Long> {

    /**
     * Most-recent credit-history rows for an SSN (caller takes the first via
     * {@code Pageable.ofSize(1)}). Kept portable across PostgreSQL and H2.
     */
    @Query("SELECT h FROM CreditHistory h WHERE h.ssn = :ssn ORDER BY h.recordedAt DESC")
    List<CreditHistory> findLatestBySsn(@Param("ssn") String ssn, Pageable pageable);
}
