package com.sih26106.emailintel.repository;

import com.sih26106.emailintel.model.RiskReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RiskReportRepository extends JpaRepository<RiskReport, Long> {
}