plugins {
    `java-library`
    application
    checkstyle
    pmd
    id("com.github.spotbugs") version "6.1.2"
    id("com.diffplug.spotless") version "7.0.3"
}

import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    val langchain4jVersion = "1.10.0"
    val langchain4jAgenticVersion = "1.10.0-beta18"
    val otelVersion = "1.43.0"
    val jacksonVersion = "2.17.2"

    implementation("org.yaml:snakeyaml:2.2")
    implementation("info.picocli:picocli:4.7.6")
    implementation("io.github.cdimascio:dotenv-java:3.2.0")
    implementation("dev.langchain4j:langchain4j-core:$langchain4jVersion")
    implementation("dev.langchain4j:langchain4j-agentic:$langchain4jAgenticVersion")
    implementation("dev.langchain4j:langchain4j-open-ai-official:$langchain4jAgenticVersion")
    implementation("io.opentelemetry:opentelemetry-api:$otelVersion")
    implementation("io.opentelemetry:opentelemetry-sdk:$otelVersion")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:$otelVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")

    testImplementation(platform("org.junit:junit-bom:5.10.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing:$otelVersion")
}

tasks.test {
    useJUnitPlatform()
    // 実行時の .env/環境変数の影響を避け、テストは常にモック前提で動作させる
    environment(
        mapOf(
            "LLM_PROVIDER" to "mock",
            "OPENAI_API_KEY" to "",
            "OPENAI_BASE_URL" to "",
            "OPENAI_MODEL" to ""
        )
    )
}

application {
    mainClass.set("io.github.hide212131.langchain4j.claude.skills.app.SkillsCliApp")
    applicationName = "skills"
}

tasks.register<Exec>("langfuseUp") {
    group = "observability"
    description = "LangFuse をローカルで起動します（公式 docker-compose を使用）。"

    val composeUrl = "https://raw.githubusercontent.com/langfuse/langfuse/main/docker-compose.yml"
    val composeFileProvider = layout.buildDirectory.file("langfuse/docker-compose.yml")

    doFirst {
        val composeFile = composeFileProvider.get().asFile
        composeFile.parentFile.mkdirs()
        if (!composeFile.exists()) {
            URL(composeUrl).openStream().use { input ->
                Files.copy(input, composeFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    // docker compose の .env 読み込みは「project-directory」を基準に決まるため、
    // Compose ファイルが build 配下にある場合でもリポジトリ直下の .env を参照できるようにする。
    workingDir = rootProject.projectDir
    commandLine(
        "docker",
        "compose",
        "-p",
        "langfuse",
        "-f",
        composeFileProvider.get().asFile.absolutePath,
        "--project-directory",
        rootProject.projectDir.absolutePath,
        "up",
        "-d"
    )
}

tasks.register<Exec>("langfuseUpForeground") {
    group = "observability"
    description = "LangFuse をローカルで起動します（フォアグラウンド。ログをそのまま表示）。"

    val composeUrl = "https://raw.githubusercontent.com/langfuse/langfuse/main/docker-compose.yml"
    val composeFileProvider = layout.buildDirectory.file("langfuse/docker-compose.yml")

    doFirst {
        val composeFile = composeFileProvider.get().asFile
        composeFile.parentFile.mkdirs()
        if (!composeFile.exists()) {
            URL(composeUrl).openStream().use { input ->
                Files.copy(input, composeFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    // docker compose の .env 読み込みは「project-directory」を基準に決まるため、
    // Compose ファイルが build 配下にある場合でもリポジトリ直下の .env を参照できるようにする。
    workingDir = rootProject.projectDir
    commandLine(
        "docker",
        "compose",
        "-p",
        "langfuse",
        "-f",
        composeFileProvider.get().asFile.absolutePath,
        "--project-directory",
        rootProject.projectDir.absolutePath,
        "up"
    )
}

tasks.register<Exec>("langfuseDown") {
    group = "observability"
    description = "LangFuse を停止します（公式 docker-compose を使用）。"

    val composeFileProvider = layout.buildDirectory.file("langfuse/docker-compose.yml")

    workingDir = rootProject.projectDir
    commandLine(
        "docker",
        "compose",
        "-p",
        "langfuse",
        "-f",
        composeFileProvider.get().asFile.absolutePath,
        "--project-directory",
        rootProject.projectDir.absolutePath,
        "down"
    )
}

tasks.register<Exec>("langfuseReset") {
    group = "observability"
    description = "LangFuse を停止し、ボリュームを削除して初期化します（注意: データ消えます）。"

    val composeFileProvider = layout.buildDirectory.file("langfuse/docker-compose.yml")

    workingDir = rootProject.projectDir
    commandLine(
        "docker",
        "compose",
        "-p",
        "langfuse",
        "-f",
        composeFileProvider.get().asFile.absolutePath,
        "--project-directory",
        rootProject.projectDir.absolutePath,
        "down",
        "-v"
    )
}

tasks.register<Exec>("langfuseLogs") {
    group = "observability"
    description = "LangFuse のログを追跡します（例: -Pservice=langfuse-web / 省略時は全サービス）。"

    val composeFileProvider = layout.buildDirectory.file("langfuse/docker-compose.yml")
    val service = providers.gradleProperty("service").orNull

    workingDir = rootProject.projectDir

    val baseArgs = mutableListOf(
        "docker",
        "compose",
        "-p",
        "langfuse",
        "-f",
        composeFileProvider.get().asFile.absolutePath,
        "--project-directory",
        rootProject.projectDir.absolutePath,
        "logs",
        "-f",
        "--tail=200"
    )
    if (!service.isNullOrBlank()) {
        baseArgs.add(service)
    }
    commandLine(baseArgs)
}

tasks.register<JavaExec>("langfuseReport") {
    group = "observability"
    description = "LangFuse API から直近トレースの gen_ai 指標を集計して表示します（鍵未設定時はスキップ）。"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.hide212131.langchain4j.claude.skills.runtime.langfuse.LangfuseReportMain")
    val limit = providers.gradleProperty("limit").orNull ?: "20"
    args("--limit", limit)
    providers.gradleProperty("traceId").orNull?.let { args("--trace-id", it) }
    providers.gradleProperty("hours").orNull?.let { args("--hours", it) }
}

tasks.register<JavaExec>("langfusePrompt") {
    group = "observability"
    description = "LangFuse API から直近トレースのプロンプト関連情報を抽出して表示します（鍵未設定時はスキップ）。"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.hide212131.langchain4j.claude.skills.runtime.langfuse.LangfusePromptMain")
    providers.gradleProperty("traceId").orNull?.let { args("--trace-id", it) }
    val limit = providers.gradleProperty("limit").orNull ?: "5"
    args("--limit", limit)
}

checkstyle {
    toolVersion = "10.12.5"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    // Allow warnings initially - can be tightened later as code is improved
    maxWarnings = 50
    isIgnoreFailures = false
}

pmd {
    toolVersion = "7.0.0"
    ruleSetFiles = files(rootProject.file("config/pmd/ruleset.xml"))
    isConsoleOutput = true
    // Don't fail the build on PMD violations initially
    isIgnoreFailures = true
}

spotbugs {
    toolVersion = "4.8.6"
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.MEDIUM
    ignoreFailures = true
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask> {
    reports.create("html") {
        required.set(true)
        setStylesheet("fancy-hist.xsl")
    }
}

spotless {
    java {
        eclipse().configFile(rootProject.file(".vscode/java-formatter.xml"))
    }
}
