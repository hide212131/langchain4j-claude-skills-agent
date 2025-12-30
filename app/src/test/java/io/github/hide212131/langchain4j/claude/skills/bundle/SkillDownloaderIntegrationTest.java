package io.github.hide212131.langchain4j.claude.skills.bundle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillDownloaderIntegrationTest {

    @Test
    void downloadsAllFilesInSkillDirectory(@TempDir Path tempDir) throws IOException {
        // Create test config
        Path configFile = tempDir.resolve("test-sources.yaml");
        String config = """
                sources:
                  - https://github.com/anthropics/skills.git#refs/heads/main:skills/pptx:test/pptx
                """;
        Files.writeString(configFile, config);

        Path outputDir = tempDir.resolve("output");

        SkillDownloader downloader = new SkillDownloader();
        SkillDownloader.SkillDownloadReport report = downloader.download(configFile, outputDir);

        assertNotNull(report);
        assertEquals(1, report.sources().size());

        SkillDownloader.ResolvedSkillSource source = report.sources().get(0);
        assertTrue(source.skillsCount() > 1, "Should download more than just SKILL.md");

        // Verify pptx skill directory
        Path pptxSkill = outputDir.resolve("test/pptx");
        assertTrue(Files.exists(pptxSkill), "pptx skill directory should exist");

        // Check for SKILL.md
        assertTrue(Files.exists(pptxSkill.resolve("SKILL.md")), "SKILL.md should exist");

        // Check for other expected files
        assertTrue(Files.exists(pptxSkill.resolve("LICENSE.txt")) || Files.exists(pptxSkill.resolve("html2pptx.md"))
                || Files.exists(pptxSkill.resolve("scripts")), "Should have files other than SKILL.md");

        // List all downloaded files
        try (Stream<Path> files = Files.walk(pptxSkill)) {
            List<Path> fileList = files.filter(Files::isRegularFile).toList();
            System.out.println("Downloaded " + fileList.size() + " files:");
            fileList.forEach(f -> System.out.println("  " + pptxSkill.relativize(f)));

            assertTrue(fileList.size() > 1, "Should have downloaded multiple files, not just SKILL.md");
        }
    }
}
