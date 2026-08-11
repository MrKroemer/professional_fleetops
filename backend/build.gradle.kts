plugins {
    java
    jacoco
    checkstyle
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "br.com.proyfebrasil"
version = "0.1.0"
description = "FleetOps — Gestão de Frotas Locadas"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val mapstructVersion = "1.6.3"
val springdocVersion = "2.8.6"

/*
 * SDK da AWS para S3. A Seção 5, item 4 exige uploads em storage S3-compatível com URLs
 * pré-assinadas, e o Compose já sobe o MinIO — que fala o protocolo S3. Não é biblioteca
 * fora da stack: é a implementação do requisito. O SDK oficial foi preferido ao cliente
 * `minio` porque assina URLs sem depender do servidor e mantém a porta aberta para trocar
 * o MinIO por qualquer outro S3 em produção, sem mexer no código.
 */
val awsSdkVersion = "2.31.30"


dependencies {
    // --- Spring Boot ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // --- Persistência ---
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.hibernate.orm:hibernate-envers")
    implementation("org.springframework.data:spring-data-envers")
    runtimeOnly("org.postgresql:postgresql")

    // --- Documentação da API (contrato do frontend) ---
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

    // --- Armazenamento de anexos (MinIO em dev, qualquer S3 em produção) ---
    implementation(platform("software.amazon.awssdk:bom:$awsSdkVersion"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:apache-client")

    // --- Mapeamento entidade <-> DTO ---
    implementation("org.mapstruct:mapstruct:$mapstructVersion")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")

    // --- Metadados de @ConfigurationProperties para a IDE ---
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // --- Testes ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // `processing` e `this-escape` produzem ruído inevitável com processadores de anotação
    // e com o modelo de proxy do Spring; o restante dos avisos é tratado como erro.
    options.compilerArgs.addAll(
        listOf("-Xlint:all,-processing,-this-escape,-serial", "-Werror", "-parameters"),
    )
}

checkstyle {
    toolVersion = "10.21.4"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxWarnings = 0
}

tasks.withType<Checkstyle>().configureEach {
    reports {
        html.required = true
        xml.required = false
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    // Fuso de exibição do sistema (RN-22); o armazenamento é sempre em UTC/timestamptz.
    systemProperty("user.timezone", "America/Recife")
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

/**
 * Cobertura mínima de 80% nas camadas `domain` e `application` de cada módulo
 * (contrato de qualidade, Seção 5.6 da especificação).
 */
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    // Filtra as classes analisadas em vez de filtrar as regras: o nome do elemento
    // no JaCoCo usa separador de diretório, o que torna `includes` por pacote frágil.
    classDirectories.setFrom(
        files(
            sourceSets.main.get().output.classesDirs.map { dir ->
                fileTree(dir) {
                    include("br/com/proyfebrasil/fleetops/**/domain/**")
                    include("br/com/proyfebrasil/fleetops/**/application/**")
                    include("br/com/proyfebrasil/fleetops/shared/money/**")
                }
            },
        ),
    )
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName = "fleetops.jar"
}
