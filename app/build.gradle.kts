plugins {
    `java-library`
    application
    checkstyle
    pmd
    id("com.github.spotbugs") version "6.1.2"
    id("com.diffplug.spotless") version "7.0.3"
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
    implementation("org.yaml:snakeyaml:2.2")
    implementation("info.picocli:picocli:4.7.6")

    testImplementation(platform("org.junit:junit-bom:5.10.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.25.3")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("io.github.hide212131.langchain4j.claude.skills.app.SkillsCliApp")
    applicationName = "skills"
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
        // Use Google Java Format
        googleJavaFormat("1.24.0")
        
        // Optionally apply import order
        importOrder()
        
        // Remove unused imports
        removeUnusedImports()
        
        // Trim trailing whitespace
        trimTrailingWhitespace()
        
        // End files with newline
        endWithNewline()
    }
}
