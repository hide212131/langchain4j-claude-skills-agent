import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.GradleException

val defaultSkillsCommit = "c74d647e56e6daa12029b6acb11a821348ad044b"
val skillsCommit = providers.gradleProperty("skillsCommit")
    .orElse(defaultSkillsCommit)
    .map { it.trim() }
    .get()

val skillsArchiveUrl = "https://codeload.github.com/anthropics/skills/tar.gz/$skillsCommit"
val skillsDownloadDir = layout.buildDirectory.dir("tmp/skills-download")
val skillsArchiveFile = layout.buildDirectory.file("tmp/skills-download/anthropics-skills.tar.gz")

tasks.register("updateSkills") {
    group = "skills"
    description = "Download anthropics/skills@$skillsCommit and export it into the local skills/ directory."

    doLast {
        val downloadDir = skillsDownloadDir.get().asFile
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        val archiveFile = skillsArchiveFile.get().asFile

        logger.lifecycle("Downloading anthropics/skills commit $skillsCommit …")
        URI(skillsArchiveUrl).toURL().openStream().use { input ->
            Files.copy(input, archiveFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        val extractedDir = downloadDir.resolve("extracted")
        project.delete(extractedDir)
        extractedDir.mkdirs()

        project.copy {
            from(tarTree(resources.gzip(archiveFile)))
            into(extractedDir)
        }

        val roots = extractedDir.listFiles { file -> file.isDirectory }
        val archiveRoot = roots?.singleOrNull()
            ?: throw GradleException("Unexpected archive layout when extracting anthropics/skills@$skillsCommit")

        val exportedSkillsDir = archiveRoot
        val sampleSkill = exportedSkillsDir.resolve("brand-guidelines")
        if (!sampleSkill.exists()) {
            throw GradleException("Exported archive from anthropics/skills@$skillsCommit does not look like the skills repository (missing brand-guidelines/).")
        }

        val targetDir = layout.projectDirectory.dir("skills").asFile
        if (targetDir.exists()) {
            logger.lifecycle("Clearing existing skills directory at ${targetDir.absolutePath}")
        }
        project.delete(targetDir)
        targetDir.mkdirs()

        project.copy {
            from(exportedSkillsDir)
            into(targetDir)
        }

        targetDir.resolve(".skills-version").writeText("$skillsCommit\n")
        logger.lifecycle("Exported anthropics/skills@$skillsCommit into ${targetDir.absolutePath}")
    }
}
