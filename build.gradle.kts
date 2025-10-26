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

fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")

val pythonEnvDir = layout.projectDirectory.dir("env/python")
val nodeEnvDir = layout.projectDirectory.dir("env/node")

tasks.register("setupPythonEnv") {
    group = "skills"
    description = "Create (or update) the Python virtual environment under env/python."

    doLast {
        val envDir = pythonEnvDir.asFile
        val pythonExecutable = if (isWindows()) "python" else "python3"
        if (!envDir.exists()) {
            envDir.mkdirs()
        }
        val venvMarker = if (isWindows()) envDir.resolve("Scripts/python.exe") else envDir.resolve("bin/python")
        if (!venvMarker.exists()) {
            exec {
                commandLine(pythonExecutable, "-m", "venv", envDir.absolutePath)
            }
        }
        val pipExecutable = if (isWindows()) envDir.resolve("Scripts/pip.exe") else envDir.resolve("bin/pip")
        if (pipExecutable.exists()) {
            exec {
                commandLine(pipExecutable.absolutePath, "install", "--upgrade", "pip", "setuptools", "wheel")
            }
        }
    }
}

tasks.register("setupNodeEnv") {
    group = "skills"
    description = "Create (or update) the Node environment under env/node."

    doLast {
        val envDir = nodeEnvDir.asFile
        if (!envDir.exists()) {
            envDir.mkdirs()
        }
        val packageJson = envDir.resolve("package.json")
        if (!packageJson.exists()) {
            exec {
                workingDir = envDir
                commandLine("npm", "init", "-y")
            }
        }
    }
}

tasks.register("setupSkillRuntimes") {
    group = "skills"
    description = "Initialise python/node virtual environments used by runScript."
    dependsOn("setupPythonEnv", "setupNodeEnv")
}

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
