package com.gustavoserafim.serviceflow.repository;

import com.gustavoserafim.serviceflow.entity.Priority;
import com.gustavoserafim.serviceflow.entity.SlaRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SlaRuleRepository extends JpaRepository<SlaRule, Long> {

    Optional<SlaRule> findByPriority(Priority priority);
}
