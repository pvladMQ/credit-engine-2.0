package com.tanzu.creditengine.repository;

import com.tanzu.creditengine.entity.AppSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppSettingsRepository extends JpaRepository<AppSettings, Long> {
}
