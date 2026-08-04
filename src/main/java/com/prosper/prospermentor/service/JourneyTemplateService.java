package com.prosper.prospermentor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prosper.prospermentor.dto.JourneyTemplateDependencyDto;
import com.prosper.prospermentor.dto.JourneyTemplateDependencyRequest;
import com.prosper.prospermentor.dto.JourneyTemplateDto;
import com.prosper.prospermentor.dto.JourneyTemplateStepDto;
import com.prosper.prospermentor.dto.JourneyTemplateStepRequest;
import com.prosper.prospermentor.dto.UpsertJourneyTemplateRequest;
import com.prosper.prospermentor.entity.JourneyStep;
import com.prosper.prospermentor.entity.JourneyStepDependency;
import com.prosper.prospermentor.entity.JourneyTemplate;
import com.prosper.prospermentor.repository.JourneyInstanceStepRepository;
import com.prosper.prospermentor.repository.JourneyStepDependencyRepository;
import com.prosper.prospermentor.repository.JourneyStepRepository;
import com.prosper.prospermentor.repository.JourneyTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JourneyTemplateService {

    private final JourneyTemplateRepository journeyTemplateRepository;
    private final JourneyStepRepository journeyStepRepository;
    private final JourneyStepDependencyRepository journeyStepDependencyRepository;
    private final JourneyInstanceStepRepository journeyInstanceStepRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<JourneyTemplateDto> getActiveTemplates() {
        return journeyTemplateRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(template -> toDto(template, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JourneyTemplateDto> getAllTemplates() {
        return journeyTemplateRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(template -> toDto(template, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<JourneyTemplate> getTemplate(UUID journeyTemplateId) {
        if (journeyTemplateId == null) {
            return Optional.empty();
        }
        return journeyTemplateRepository.findById(journeyTemplateId);
    }

    @Transactional(readOnly = true)
    public Optional<JourneyTemplateDto> getTemplateDto(UUID journeyTemplateId) {
        return getTemplate(journeyTemplateId).map(template -> toDto(template, true));
    }

    @Transactional
    public JourneyTemplateDto createTemplate(UpsertJourneyTemplateRequest request) {
        JourneyTemplate template = new JourneyTemplate();
        template.setTemplateVersion(1);
        applyTemplateRequest(template, request, false);
        JourneyTemplate saved = journeyTemplateRepository.save(template);
        persistTemplateGraph(saved, request);
        return getTemplateDto(saved.getId())
                .orElseThrow(() -> new NoSuchElementException("Journey template not found after creation"));
    }

    @Transactional
    public JourneyTemplateDto updateTemplate(UUID journeyTemplateId, UpsertJourneyTemplateRequest request) {
        JourneyTemplate template = journeyTemplateRepository.findById(journeyTemplateId)
                .orElseThrow(() -> new NoSuchElementException("Journey template not found"));

        boolean templateInUse = journeyInstanceStepRepository.existsByJourneyStep_JourneyTemplate_Id(journeyTemplateId);
        if (templateInUse && !matchesCurrentGraph(template, request)) {
            throw new IllegalStateException("This journey template already has live employee journeys. Create a new template to change milestones or dependencies.");
        }

        applyTemplateRequest(template, request, true);
        journeyTemplateRepository.save(template);
        if (!templateInUse) {
            persistTemplateGraph(template, request);
        }
        return getTemplateDto(template.getId())
                .orElseThrow(() -> new NoSuchElementException("Journey template not found after update"));
    }

    private void applyTemplateRequest(JourneyTemplate template,
                                      UpsertJourneyTemplateRequest request,
                                      boolean incrementVersion) {
        validateRequest(request);

        template.setName(request.getName().trim());
        template.setProgramType(normalizeNullable(request.getProgramType()));
        template.setDescription(normalizeNullable(request.getDescription()));
        template.setCoverImageUrl(normalizeNullable(request.getCoverImageUrl()));
        template.setDefaultDurationWeeks(request.getDefaultDurationWeeks());
        template.setActive(request.getActive() == null || request.getActive());
        template.setTemplateVersion(incrementVersion
                ? Math.max(1, template.getTemplateVersion() != null ? template.getTemplateVersion() + 1 : 2)
                : Math.max(1, template.getTemplateVersion() != null ? template.getTemplateVersion() : 1));
        template.setTemplateSnapshotJson(buildTemplateSnapshotJson(request));
    }

    private void persistTemplateGraph(JourneyTemplate template, UpsertJourneyTemplateRequest request) {
        journeyStepDependencyRepository.deleteByJourneyTemplate_Id(template.getId());
        journeyStepRepository.deleteByJourneyTemplate_Id(template.getId());

        List<JourneyTemplateStepRequest> requestedSteps = request.getSteps() != null ? request.getSteps() : List.of();
        Map<String, JourneyStep> stepsByKey = new LinkedHashMap<>();
        List<JourneyStep> stepsToSave = new ArrayList<>();

        int sequence = 1;
        for (JourneyTemplateStepRequest stepRequest : requestedSteps) {
            String normalizedStepKey = stepRequest.getStepKey().trim();
            if (stepsByKey.containsKey(normalizedStepKey)) {
                throw new IllegalArgumentException("Duplicate stepKey in journey template: " + normalizedStepKey);
            }

            JourneyStep step = new JourneyStep();
            step.setJourneyTemplate(template);
            step.setStepKey(normalizedStepKey);
            step.setDefaultSequence(sequence++);
            step.setTitle(stepRequest.getTitle().trim());
            step.setDescription(normalizeNullable(stepRequest.getDescription()));
            step.setStepType(stepRequest.getStepType());
            step.setRequired(stepRequest.getRequired() == null || stepRequest.getRequired());
            step.setDefaultDueOffsetDays(stepRequest.getDefaultDueOffsetDays());
            step.setStepConfigJson(normalizeNullable(stepRequest.getStepConfigJson()));

            stepsByKey.put(normalizedStepKey, step);
            stepsToSave.add(step);
        }

        List<JourneyStep> savedSteps = journeyStepRepository.saveAll(stepsToSave);
        Map<String, JourneyStep> savedStepsByKey = savedSteps.stream()
                .collect(LinkedHashMap::new, (map, step) -> map.put(step.getStepKey(), step), Map::putAll);

        List<JourneyTemplateDependencyRequest> requestedDependencies = normalizeDependencies(request);
        List<JourneyStepDependency> dependenciesToSave = new ArrayList<>();
        for (JourneyTemplateDependencyRequest dependencyRequest : requestedDependencies) {
            JourneyStep fromStep = savedStepsByKey.get(dependencyRequest.getFromStepKey().trim());
            JourneyStep toStep = savedStepsByKey.get(dependencyRequest.getToStepKey().trim());

            if (fromStep == null || toStep == null) {
                throw new IllegalArgumentException("Dependency references a missing step key");
            }
            if (fromStep.getId() != null && fromStep.getId().equals(toStep.getId())) {
                throw new IllegalArgumentException("A step cannot depend on itself");
            }

            JourneyStepDependency dependency = new JourneyStepDependency();
            dependency.setJourneyTemplate(template);
            dependency.setFromStep(fromStep);
            dependency.setToStep(toStep);
            dependency.setDependencyType(dependencyRequest.getDependencyType());
            dependenciesToSave.add(dependency);
        }

        if (!dependenciesToSave.isEmpty()) {
            journeyStepDependencyRepository.saveAll(dependenciesToSave);
        }
    }

    private List<JourneyTemplateDependencyRequest> normalizeDependencies(UpsertJourneyTemplateRequest request) {
        List<JourneyTemplateDependencyRequest> providedDependencies = request.getDependencies() != null
                ? request.getDependencies().stream()
                .filter(dependency -> dependency != null
                        && dependency.getFromStepKey() != null
                        && !dependency.getFromStepKey().trim().isEmpty()
                        && dependency.getToStepKey() != null
                        && !dependency.getToStepKey().trim().isEmpty()
                        && dependency.getDependencyType() != null)
                .toList()
                : List.of();

        if (!providedDependencies.isEmpty()) {
            return providedDependencies;
        }

        List<JourneyTemplateStepRequest> steps = request.getSteps() != null ? request.getSteps() : List.of();
        if (steps.size() <= 1) {
            return List.of();
        }

        List<JourneyTemplateDependencyRequest> linearDependencies = new ArrayList<>();
        for (int index = 0; index < steps.size() - 1; index++) {
            linearDependencies.add(new JourneyTemplateDependencyRequest(
                    steps.get(index).getStepKey().trim(),
                    steps.get(index + 1).getStepKey().trim(),
                    JourneyStepDependency.DependencyType.FINISH_TO_START
            ));
        }
        return linearDependencies;
    }

    private void validateRequest(UpsertJourneyTemplateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Journey template request is required");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Template name is required");
        }
        if (request.getSteps() == null || request.getSteps().isEmpty()) {
            throw new IllegalArgumentException("At least one journey step is required");
        }
        for (JourneyTemplateStepRequest stepRequest : request.getSteps()) {
            if (stepRequest == null) {
                throw new IllegalArgumentException("Journey steps cannot contain null values");
            }
            if (stepRequest.getStepKey() == null || stepRequest.getStepKey().trim().isEmpty()) {
                throw new IllegalArgumentException("Each journey step requires a stepKey");
            }
            if (stepRequest.getTitle() == null || stepRequest.getTitle().trim().isEmpty()) {
                throw new IllegalArgumentException("Each journey step requires a title");
            }
            if (stepRequest.getStepType() == null) {
                throw new IllegalArgumentException("Each journey step requires a stepType");
            }
        }
    }

    private boolean matchesCurrentGraph(JourneyTemplate template, UpsertJourneyTemplateRequest request) {
        List<JourneyStep> currentSteps = journeyStepRepository.findByJourneyTemplate_IdOrderByDefaultSequenceAsc(template.getId());
        List<JourneyStepDependency> currentDependencies = journeyStepDependencyRepository.findByJourneyTemplate_Id(template.getId());

        List<String> normalizedCurrentSteps = currentSteps.stream()
                .map(step -> String.join("|",
                        step.getStepKey(),
                        normalizeNullable(step.getTitle()) != null ? normalizeNullable(step.getTitle()) : "",
                        normalizeNullable(step.getDescription()) != null ? normalizeNullable(step.getDescription()) : "",
                        step.getStepType() != null ? step.getStepType().name() : "",
                        String.valueOf(step.getRequired() != null ? step.getRequired() : true),
                        String.valueOf(step.getDefaultDueOffsetDays() != null ? step.getDefaultDueOffsetDays() : ""),
                        normalizeNullable(step.getStepConfigJson()) != null ? normalizeNullable(step.getStepConfigJson()) : ""))
                .toList();

        List<String> normalizedRequestedSteps = request.getSteps().stream()
                .map(step -> String.join("|",
                        step.getStepKey().trim(),
                        step.getTitle().trim(),
                        normalizeNullable(step.getDescription()) != null ? normalizeNullable(step.getDescription()) : "",
                        step.getStepType() != null ? step.getStepType().name() : "",
                        String.valueOf(step.getRequired() == null || step.getRequired()),
                        String.valueOf(step.getDefaultDueOffsetDays() != null ? step.getDefaultDueOffsetDays() : ""),
                        normalizeNullable(step.getStepConfigJson()) != null ? normalizeNullable(step.getStepConfigJson()) : ""))
                .toList();

        if (!normalizedCurrentSteps.equals(normalizedRequestedSteps)) {
            return false;
        }

        List<String> normalizedCurrentDependencies = currentDependencies.stream()
                .map(dependency -> String.join("|",
                        dependency.getFromStep() != null ? dependency.getFromStep().getStepKey() : "",
                        dependency.getToStep() != null ? dependency.getToStep().getStepKey() : "",
                        dependency.getDependencyType() != null ? dependency.getDependencyType().name() : ""))
                .sorted()
                .toList();

        List<String> normalizedRequestedDependencies = normalizeDependencies(request).stream()
                .map(dependency -> String.join("|",
                        dependency.getFromStepKey().trim(),
                        dependency.getToStepKey().trim(),
                        dependency.getDependencyType().name()))
                .sorted()
                .toList();

        return normalizedCurrentDependencies.equals(normalizedRequestedDependencies);
    }

    private JourneyTemplateDto toDto(JourneyTemplate template, boolean includeDetails) {
        List<JourneyStep> steps = journeyStepRepository.findByJourneyTemplate_IdOrderByDefaultSequenceAsc(template.getId());
        List<JourneyStepDependency> dependencies = journeyStepDependencyRepository.findByJourneyTemplate_Id(template.getId());

        return JourneyTemplateDto.builder()
                .id(template.getId())
                .name(template.getName())
                .programType(template.getProgramType())
                .description(template.getDescription())
                .coverImageUrl(template.getCoverImageUrl())
                .defaultDurationWeeks(template.getDefaultDurationWeeks())
                .templateVersion(template.getTemplateVersion())
                .active(template.getActive())
                .stepCount(steps.size())
                .dependencyCount(dependencies.size())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .steps(includeDetails ? steps.stream().map(this::toStepDto).toList() : null)
                .dependencies(includeDetails ? dependencies.stream().map(this::toDependencyDto).toList() : null)
                .build();
    }

    private JourneyTemplateStepDto toStepDto(JourneyStep step) {
        return JourneyTemplateStepDto.builder()
                .id(step.getId())
                .stepKey(step.getStepKey())
                .defaultSequence(step.getDefaultSequence())
                .title(step.getTitle())
                .description(step.getDescription())
                .stepType(step.getStepType())
                .required(step.getRequired())
                .defaultDueOffsetDays(step.getDefaultDueOffsetDays())
                .stepConfigJson(step.getStepConfigJson())
                .build();
    }

    private JourneyTemplateDependencyDto toDependencyDto(JourneyStepDependency dependency) {
        JourneyStep fromStep = dependency.getFromStep();
        JourneyStep toStep = dependency.getToStep();
        return JourneyTemplateDependencyDto.builder()
                .id(dependency.getId())
                .fromStepId(fromStep != null ? fromStep.getId() : null)
                .fromStepKey(fromStep != null ? fromStep.getStepKey() : null)
                .fromStepTitle(fromStep != null ? fromStep.getTitle() : null)
                .toStepId(toStep != null ? toStep.getId() : null)
                .toStepKey(toStep != null ? toStep.getStepKey() : null)
                .toStepTitle(toStep != null ? toStep.getTitle() : null)
                .dependencyType(dependency.getDependencyType())
                .build();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildTemplateSnapshotJson(UpsertJourneyTemplateRequest request) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", request.getName() != null ? request.getName().trim() : null);
        snapshot.put("programType", normalizeNullable(request.getProgramType()));
        snapshot.put("coverImageUrl", normalizeNullable(request.getCoverImageUrl()));
        snapshot.put("defaultDurationWeeks", request.getDefaultDurationWeeks());
        snapshot.put("stepCount", request.getSteps() != null ? request.getSteps().size() : 0);
        snapshot.put("dependencyCount", normalizeDependencies(request).size());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }
}
