package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.config.DecisionProperties;
import com.emergencias.emergency_management.dto.DecisionResponse;
import com.emergencias.emergency_management.dto.ResourceRecommendation;
import com.emergencias.emergency_management.model.Incident;
import com.emergencias.emergency_management.repository.DecisionRuleRepository;
import com.emergencias.emergency_management.repository.IncidentRepository;
import com.emergencias.emergency_management.repository.ResourceRepository;
import com.emergencias.emergency_management.repository.projection.ApplicableRuleRow;
import com.emergencias.emergency_management.repository.projection.ResourceCandidateRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DecisionService {

    private final DecisionProperties decisionProperties;
    private final IncidentRepository incidentRepository;
    private final ResourceRepository resourceRepository;
    private final DecisionRuleRepository decisionRuleRepository;

    @Transactional(readOnly = true)
    public DecisionResponse recommend(String incidentId) {

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No existe el incidente " + incidentId));

        List<ApplicableRuleRow> applicableRules =
                decisionRuleRepository.findApplicableRules(incidentId);

        Optional<ApplicableRuleRow> rule = selectRule(applicableRules);

        if (rule.isEmpty()) {
            log.warn(
                    "No hay reglas aplicables para el incidente {}",
                    incidentId);

            return DecisionResponse.builder()
                    .incidentId(incident.getId())
                    .incidentType(incident.getType())
                    .severity(incident.getSeverity())
                    .ruleApplied(
                            "No hay una regla activa para este tipo "
                                    + "de incidente y gravedad.")
                    .recommendations(List.of())
                    .build();
        }

        ApplicableRuleRow selectedRule = rule.get();

        List<String> eligibleTypes =
                resourceTypesOf(selectedRule, applicableRules);

        List<ResourceCandidateRow> candidates =
                eligibleTypes.isEmpty()
                        ? List.of()
                        : resourceRepository.findAvailableForIncident(
                        incidentId,
                        eligibleTypes,
                        decisionProperties.getMaxDistanceKm(),
                        decisionProperties.getMaxTravelTimeMinutes());

        List<ResourceRecommendation> recommendations =
                candidates.stream()
                        .map(candidate -> {

                            String routeDescription =
                                    candidate.route() == null
                                            ? candidate.zoneName()
                                            : String.join(" → ", candidate.route());

                            String reason = String.format(
                                    java.util.Locale.ROOT,
                                    "Ruta: %s. Distancia estimada: %.1f km. "
                                            + "Tiempo estimado: %.0f min. "
                                            + "Recurso disponible y compatible "
                                            + "con la regla %s.",
                                    routeDescription,
                                    candidate.distanceKm(),
                                    candidate.travelTimeMinutes(),
                                    selectedRule.ruleName());

                            return ResourceRecommendation.builder()
                                    .resourceId(candidate.id())
                                    .name(candidate.name())
                                    .type(candidate.type())
                                    .zoneName(candidate.zoneName())
                                    .distanceKm(candidate.distanceKm())
                                    .travelTimeMinutes(
                                            candidate.travelTimeMinutes())
                                    .route(candidate.route())
                                    .reason(reason)
                                    .build();
                        })
                        .toList();

        String ruleDescription =
                selectedRule.ruleName()
                        + ": " + selectedRule.description()
                        + " Tipos admitidos: "
                        + String.join(", ", eligibleTypes);

        log.info(
                "Decisión para incidente {} mediante regla {}: "
                        + "{} recursos recomendados",
                incidentId,
                selectedRule.ruleId(),
                recommendations.size());

        return DecisionResponse.builder()
                .incidentId(incident.getId())
                .incidentType(incident.getType())
                .severity(incident.getSeverity())
                .ruleApplied(ruleDescription)
                .recommendations(recommendations)
                .build();
    }

    public List<String> eligibleResourceTypes(String incidentId) {

        List<ApplicableRuleRow> applicableRules =
                decisionRuleRepository.findApplicableRules(incidentId);

        return selectRule(applicableRules)
                .map(rule -> resourceTypesOf(rule, applicableRules))
                .orElse(List.of());
    }

    private Optional<ApplicableRuleRow> selectRule(
            List<ApplicableRuleRow> applicableRules) {

        return applicableRules.stream()
                .max(
                        Comparator.comparingInt(
                                        ApplicableRuleRow::priority)
                                .thenComparing(
                                        ApplicableRuleRow::ruleId));
    }

    private List<String> resourceTypesOf(
            ApplicableRuleRow selectedRule,
            List<ApplicableRuleRow> applicableRules) {

        return applicableRules.stream()
                .filter(rule -> rule.ruleId()
                        .equals(selectedRule.ruleId()))
                .map(ApplicableRuleRow::resourceType)
                .distinct()
                .sorted()
                .toList();
    }
}
