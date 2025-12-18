plugins {
    `java-library`
    application
    checkstyle
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
