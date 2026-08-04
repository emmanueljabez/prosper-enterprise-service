package com.prosper.prospermentor.service.support;

import com.prosper.prospermentor.dto.CompanyProgramCatalogStageDto;
import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramCatalogProgram;
import com.prosper.prospermentor.entity.Program;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public final class CompanyProgramCatalogSupport {

    private CompanyProgramCatalogSupport() {
    }

    public static List<CompanyProgramCatalogProgram> orderedStages(CompanyProgram companyProgram) {
        if (companyProgram == null) {
            return List.of();
        }

        List<CompanyProgramCatalogProgram> catalogPrograms = companyProgram.getCatalogPrograms();
        if (catalogPrograms != null && !catalogPrograms.isEmpty()) {
            return catalogPrograms.stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(
                            CompanyProgramCatalogProgram::getJourneyOrder,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    ))
                    .toList();
        }

        if (companyProgram.getProgram() == null) {
            return List.of();
        }

        CompanyProgramCatalogProgram fallback = new CompanyProgramCatalogProgram();
        fallback.setProgram(companyProgram.getProgram());
        fallback.setJourneyOrder(1);
        fallback.setStageType(CompanyProgramCatalogProgram.StageType.CORE);
        return List.of(fallback);
    }

    public static Program anchorProgram(CompanyProgram companyProgram) {
        return orderedStages(companyProgram).stream()
                .map(CompanyProgramCatalogProgram::getProgram)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(companyProgram != null ? companyProgram.getProgram() : null);
    }

    public static List<UUID> orderedProgramIds(CompanyProgram companyProgram) {
        return orderedStages(companyProgram).stream()
                .map(CompanyProgramCatalogProgram::getProgram)
                .filter(Objects::nonNull)
                .map(Program::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    public static List<CompanyProgramCatalogStageDto> toStageDtos(CompanyProgram companyProgram) {
        return orderedStages(companyProgram).stream()
                .map(stage -> CompanyProgramCatalogStageDto.builder()
                        .id(stage.getId())
                        .programId(stage.getProgram() != null ? stage.getProgram().getId() : null)
                        .programName(stage.getProgram() != null ? stage.getProgram().getName() : null)
                        .programDescription(stage.getProgram() != null ? stage.getProgram().getDescription() : null)
                        .journeyOrder(stage.getJourneyOrder())
                        .journeyStageName(stage.getJourneyStageName())
                        .stageType(stage.getStageType())
                        .build())
                .toList();
    }

    public static String buildJourneySummary(CompanyProgram companyProgram) {
        List<String> programNames = orderedStages(companyProgram).stream()
                .map(CompanyProgramCatalogProgram::getProgram)
                .filter(Objects::nonNull)
                .map(Program::getName)
                .filter(Objects::nonNull)
                .filter(name -> !name.isBlank())
                .toList();

        if (programNames.isEmpty()) {
            return null;
        }

        if (programNames.size() <= 3) {
            return String.join(" -> ", programNames);
        }

        return programNames.subList(0, 3).stream().collect(Collectors.joining(" -> ")) + " +" + (programNames.size() - 3) + " more";
    }
}
