package com.smartscraper.repository;

import com.smartscraper.entity.ScheduledJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduledJobRepository extends JpaRepository<ScheduledJob, Long> {
}

