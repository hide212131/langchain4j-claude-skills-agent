package io.github.hide212131.langchain4j.claude.skills.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "io.github.hide212131.langchain4j.claude.skills",
        importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {

    private static final String APP = "io.github.hide212131.langchain4j.claude.skills.app..";
    private static final String RUNTIME = "io.github.hide212131.langchain4j.claude.skills.runtime..";
    private static final String INFRA = "io.github.hide212131.langchain4j.claude.skills.infra..";

    @ArchTest
    static final ArchRule appModuleShouldOnlyDependOnAllowedLayers =
            classes()
                    .that()
                    .resideInAPackage(APP)
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage(
                            APP,
                            RUNTIME,
                            "java..",
                            "javax..",
                            "picocli..",
                            "dev.langchain4j..");

    @ArchTest
    static final ArchRule runtimeModuleShouldNotDependOnAppNorOtherLayers =
            classes()
                    .that()
                    .resideInAPackage(RUNTIME)
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage(
                            RUNTIME,
                            INFRA,
                            "java..",
                            "javax..",
                            "org.slf4j..",
                            "dev.langchain4j..",
                            "org.yaml..",
                            "com.fasterxml.jackson..");

    @ArchTest
    static final ArchRule infraModuleShouldBeLeafLayer =
            classes()
                    .that()
                    .resideInAPackage(INFRA)
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage(INFRA, "java..", "javax..", "org.slf4j..");
}
