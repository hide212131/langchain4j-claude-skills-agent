package io.github.hide212131.langchain4j.claude.skills.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test to ensure ArchUnit + JUnit setup works.
 * Real architectural rules are introduced in P0-0 Red phase.
 */
class ArchitectureSmokeTest {

    @Test
    void archUnitShouldImportJdkPackages() {
        JavaClasses imported = new ClassFileImporter().importPackages("java.util");

        assertThat(imported).isNotEmpty();
    }
}
