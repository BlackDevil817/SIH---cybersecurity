package com.sih26106.emailintel.repository;

import com.sih26106.emailintel.model.Ioc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface IocRepository extends JpaRepository<Ioc, Long> {
    List<Ioc> findByEmailAnalysisId(Long emailAnalysisId);
    List<Ioc> findByTypeAndValue(String type, String value);
}