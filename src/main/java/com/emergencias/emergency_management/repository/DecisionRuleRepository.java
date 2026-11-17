package com.emergencias.emergency_management.repository;

import com.emergencias.emergency_management.model.DecisionRule;
import com.emergencias.emergency_management.repository.projection.ApplicableRuleRow;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Queries over the decision rules.
 */
public interface DecisionRuleRepository
        extends Neo4jRepository<DecisionRule, String> {

    /**
     * Resource types required by the active rules that match the type and
     * severity of the incident, one row per type.
     */
    @Query("""
        MATCH (i:Incident {id: $incidentId})
        MATCH (rule:DecisionRule)
        WHERE rule.incidentType = i.type
          AND rule.active = true
          AND i.severity >= rule.minSeverity
          AND i.severity <= rule.maxSeverity

        MATCH (rule)-[allows:ALLOWS_RESOURCE_TYPE]->(rt:ResourceType)

        RETURN rule.id AS ruleId,
               rule.name AS ruleName,
               rule.description AS description,
               rule.priority AS priority,
               rt.name AS resourceType,
               coalesce(allows.group, rt.name) AS requirementGroup,
               coalesce(allows.quantity, 1) AS quantity

        ORDER BY rule.priority DESC, rule.id, rt.name
        """)
    List<ApplicableRuleRow> findApplicableRules(
            @Param("incidentId") String incidentId);
}