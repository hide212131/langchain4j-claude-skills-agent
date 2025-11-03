import org.gradle.api.tasks.JavaExec

plugins {
    `java-library`
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("dev.langchain4j:langchain4j-bom:1.7.1"))
    implementation(platform("io.opentelemetry:opentelemetry-bom:1.44.1"))
    implementation("dev.langchain4j:langchain4j-agentic")
    implementation("dev.langchain4j:langchain4j-open-ai")
    implementation("dev.langchain4j:langchain4j-core")
    implementation("dev.langchain4j:langchain4j")
    implementation("io.opentelemetry:opentelemetry-api")
    implementation("io.opentelemetry:opentelemetry-sdk")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("io.opentelemetry.semconv:opentelemetry-semconv:1.28.0-alpha")
    implementation("info.picocli:picocli:4.7.6")
    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.13")

    annotationProcessor("info.picocli:picocli-codegen:4.7.6")
    compileOnly("info.picocli:picocli-codegen:4.7.6")

    testImplementation(platform("org.junit:junit-bom:5.10.1"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}

tasks.test {
    useJUnitPlatform()
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
    }
}

application {
    mainClass.set("io.github.hide212131.langchain4j.claude.skills.app.cli.SkillsCliApp")
    applicationName = "skills"
}

val langfusePrompt by tasks.registering(JavaExec::class) {
    group = "langfuse"
    description = "Prints workflow.act llm.chat prompts from Langfuse"
    mainClass.set("io.github.hide212131.langchain4j.claude.skills.app.langfuse.LangfusePromptTool")
    classpath = sourceSets["main"].runtimeClasspath

    val traceId = (project.findProperty("traceId") as? String)?.takeIf { it.isNotBlank() }
    traceId?.let {
        args("--trace-id", it)
    }

    project.findProperty("index")?.toString()?.takeIf { it.isNotBlank() }?.let {
        args("--index", it)
    }

    project.findProperty("all")?.toString()?.let {
        if (it.equals("true", ignoreCase = true)) {
            args("--all")
        }
    }

    project.findProperty("baseUrl")?.toString()?.takeIf { it.isNotBlank() }?.let {
        args("--base-url", it)
    }
    project.findProperty("publicKey")?.toString()?.takeIf { it.isNotBlank() }?.let {
        args("--public-key", it)
    }
    project.findProperty("secretKey")?.toString()?.takeIf { it.isNotBlank() }?.let {
        args("--secret-key", it)
    }
}

tasks.named("run") {
    dependsOn(rootProject.tasks.named("setupSkillRuntimes"))
}
