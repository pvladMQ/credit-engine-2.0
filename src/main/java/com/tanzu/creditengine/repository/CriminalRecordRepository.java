package com.tanzu.creditengine.repository;

import com.tanzu.creditengine.entity.CriminalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CriminalRecordRepository extends JpaRepository<CriminalRecord, Long> {

    List<CriminalRecord> findBySsn(String ssn);

    @Query("SELECT COUNT(c) FROM CriminalRecord c WHERE c.ssn = :ssn")
    long countBySsn(@Param("ssn") String ssn);

    @Query("SELECT COUNT(c) FROM CriminalRecord c WHERE c.ssn = :ssn AND c.severity = :severity")
    long countBySsnAndSeverity(@Param("ssn") String ssn, @Param("severity") String severity);
}
