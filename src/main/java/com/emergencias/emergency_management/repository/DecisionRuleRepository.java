package com.emergencias.emergency_management.repository;

import com.emergencias.emergency_management.model.DecisionRule;
import com.emergencias.emergency_management.repository.projection.ApplicableRuleRow;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DecisionRuleRepository
        extends Neo4jRepository<DecisionRule, String> {

    @Query("""
        MATCH (i:Incident {id: $incidentId})
        MATCH (rule:DecisionRule)
        WHERE rule.incidentType = i.type
          AND rule.active = true
          AND i.severity >= rule.minSeverity
          AND i.severity <= rule.maxSeverity

        MATCH (rule)-[:ALLOWS_RESOURCE_TYPE]->(rt:ResourceType)

        RETURN rule.id AS ruleId,
               rule.name AS ruleName,
               rule.description AS description,
               rule.priority AS priority,
               rt.name AS resourceType

        ORDER BY rule.priority DESC, rule.id, rt.name
        """)
    List<ApplicableRuleRow> findApplicableRules(
            @Param("incidentId") String incidentId);
}