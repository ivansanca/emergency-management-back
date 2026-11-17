package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.config.DecisionProperties;
import com.emergencias.emergency_management.dto.DecisionResponse;
import com.emergencias.emergency_management.dto.ResourceRecommendation;
import com.emergencias.emergency_management.dto.ResourceShortfall;
import com.emergencias.emergency_management.exception.NotFoundException;
import com.emergencias.emergency_management.model.Incident;
import com.emergencias.emergency_management.repository.DecisionRuleRepository;
import com.emergencias.emergency_management.repository.IncidentRepository;
import com.emergencias.emergency_management.repository.ResourceRepository;
import com.emergencias.emergency_management.repository.projection.ApplicableRuleRow;
import com.emergencias.emergency_management.repository.projection.ResourceCandidateRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Deterministic recommendation engine.
 * <p>
 * It selects the rule that applies to the incident and, for each requirement
 * of the rule, picks the fastest available resources. Requirements that cannot
 * be covered are reported instead of failing. The same incident over the same
 * graph always produces the same recommendation.
 */
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
                .orElseThrow(() -> new NotFoundException(
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
                    .shortfalls(List.of())
                    .build();
        }

        ApplicableRuleRow selectedRule = rule.get();

        List<Requirement> requirements =
                requirementsOf(selectedRule, applicableRules);

        List<String> eligibleTypes =
                resourceTypesOf(selectedRule, applicableRules);

        List<ResourceCandidateRow> candidates = new ArrayList<>();
        if (!eligibleTypes.isEmpty()) {
            candidates.addAll(resourceRepository.findAvailableForIncident(
                    incidentId,
                    eligibleTypes,
                    decisionProperties.getMaxDistanceKm(),
                    decisionProperties.getMaxTravelTimeMinutes()));
            candidates.addAll(
                    resourceRepository.findAvailableAerialResources(
                            eligibleTypes));
        }

        Set<String> selectedIds = new HashSet<>();
        List<ResourceShortfall> shortfalls = new ArrayList<>();

        for (Requirement requirement : requirements) {
            List<ResourceCandidateRow> picked = candidates.stream()
                    .filter(candidate -> requirement.types()
                            .contains(candidate.type()))
                    .filter(candidate -> !selectedIds.contains(candidate.id()))
                    .limit(requirement.quantity())
                    .toList();

            picked.forEach(candidate -> selectedIds.add(candidate.id()));

            if (picked.size() < requirement.quantity()) {
                shortfalls.add(ResourceShortfall.builder()
                        .requirement(requirement.label())
                        .requested(requirement.quantity())
                        .found(picked.size())
                        .build());
            }
        }

        List<ResourceRecommendation> recommendations = candidates.stream()
                .filter(candidate -> selectedIds.contains(candidate.id()))
                .map(candidate -> toRecommendation(
                        candidate, selectedRule.ruleName()))
                .toList();

        String ruleDescription =
                selectedRule.ruleName()
                        + ": " + selectedRule.description()
                        + " Necesidades: "
                        + requirements.stream()
                        .map(requirement -> requirement.quantity()
                                + " x " + requirement.label())
                        .collect(Collectors.joining(", "));

        log.info(
                "Decisión para incidente {} mediante regla {}: "
                        + "{} recursos recomendados, {} necesidades sin cubrir",
                incidentId,
                selectedRule.ruleId(),
                recommendations.size(),
                shortfalls.size());

        return DecisionResponse.builder()
                .incidentId(incident.getId())
                .incidentType(incident.getType())
                .severity(incident.getSeverity())
                .ruleApplied(ruleDescription)
                .recommendations(recommendations)
                .shortfalls(shortfalls)
                .build();
    }

    public List<String> eligibleResourceTypes(String incidentId) {

        List<ApplicableRuleRow> applicableRules =
                decisionRuleRepository.findApplicableRules(incidentId);

        return selectRule(applicableRules)
                .map(rule -> resourceTypesOf(rule, applicableRules))
                .orElse(List.of());
    }

    private ResourceRecommendation toRecommendation(
            ResourceCandidateRow candidate, String ruleName) {

        if (candidate.travelTimeMinutes() == null) {
            String reason = String.format(
                    Locale.ROOT,
                    "Medio aéreo con base en %s. Se moviliza sin cálculo "
                            + "de ruta por carretera. Recurso disponible "
                            + "y compatible con la regla %s.",
                    candidate.zoneName(),
                    ruleName);

            return ResourceRecommendation.builder()
                    .resourceId(candidate.id())
                    .name(candidate.name())
                    .type(candidate.type())
                    .zoneName(candidate.zoneName())
                    .route(List.of())
                    .reason(reason)
                    .build();
        }

        double distanceKm = roundToOneDecimal(candidate.distanceKm());
        double travelTimeMinutes =
                roundToOneDecimal(candidate.travelTimeMinutes());

        String routeDescription =
                candidate.route() == null
                        ? candidate.zoneName()
                        : String.join(" → ", candidate.route());

        String reason = String.format(
                Locale.ROOT,
                "Ruta: %s. Distancia estimada: %.1f km. "
                        + "Tiempo estimado: %.0f min. "
                        + "Recurso disponible y compatible "
                        + "con la regla %s.",
                routeDescription,
                distanceKm,
                travelTimeMinutes,
                ruleName);

        return ResourceRecommendation.builder()
                .resourceId(candidate.id())
                .name(candidate.name())
                .type(candidate.type())
                .zoneName(candidate.zoneName())
                .distanceKm(distanceKm)
                .travelTimeMinutes(travelTimeMinutes)
                .route(candidate.route())
                .reason(reason)
                .build();
    }

    private static double roundToOneDecimal(double value) {
        return Math.round(value * 10) / 10.0;
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

    private List<Requirement> requirementsOf(
            ApplicableRuleRow selectedRule,
            List<ApplicableRuleRow> applicableRules) {

        Map<String, Set<String>> typesByGroup = new TreeMap<>();
        Map<String, Integer> quantityByGroup = new TreeMap<>();

        applicableRules.stream()
                .filter(rule -> rule.ruleId()
                        .equals(selectedRule.ruleId()))
                .forEach(rule -> {
                    typesByGroup
                            .computeIfAbsent(
                                    rule.requirementGroup(),
                                    group -> new TreeSet<>())
                            .add(rule.resourceType());
                    quantityByGroup.merge(
                            rule.requirementGroup(),
                            rule.quantity(),
                            Math::max);
                });

        return typesByGroup.entrySet().stream()
                .map(entry -> new Requirement(
                        entry.getValue(),
                        quantityByGroup.get(entry.getKey())))
                .sorted(Comparator.comparing(Requirement::label))
                .toList();
    }

    private record Requirement(Set<String> types, int quantity) {

        String label() {
            return String.join("/", types);
        }
    }
}
