package com.prosper.prospermentor.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationVersionTest {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+)__.+\\.sql$");

    @Test
    void migrations_shouldNotReuseFlywayVersionNumbers() throws Exception {
        Path migrationDirectory = Path.of("src/main/resources/db/migration");

        Map<String, Long> versionCounts = Files.list(migrationDirectory)
                .map(path -> path.getFileName().toString())
                .map(VERSION_PATTERN::matcher)
                .filter(Matcher::matches)
                .collect(Collectors.groupingBy(matcher -> matcher.group(1), Collectors.counting()));

        Map<String, Long> duplicateVersions = versionCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertThat(duplicateVersions).isEmpty();
    }
}
