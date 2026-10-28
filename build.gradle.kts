import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"

    // Kotlin 전환 (Java 와 한 프로젝트에서 함께 컴파일)
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"   // @Service, @Transactional 등이 붙은 클래스를 자동으로 open
    kotlin("plugin.jpa") version "2.3.21"      // @Entity 에 기본 생성자를 자동 생성
}

group = "com.back"
version = "0.0.1"
description = "NBE-12-14-2-Team06"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    compilerOptions {
        // Spring 의 null 안전 어노테이션(@Nullable 등)을 Kotlin 타입에 엄격하게 반영
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

// JPA 엔티티는 지연 로딩 프록시를 만들 수 있어야 해서 open 이어야 한다 (Kotlin 클래스는 기본이 final)
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

repositories {
    mavenCentral()
}

extra["springAiVersion"] = "2.0.1"

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

dependencies {
    // 웹 / 영속성
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin") // JSON 변환 시 Kotlin 기본값·null 타입 지원

    // 인증 / 인가
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    // API 문서 (Swagger)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0")

    // DB 드라이버
    runtimeOnly("org.postgresql:postgresql")

    // 개발 편의
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // 테스트
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("com.h2database:h2") // 테스트 전용 인메모리 DB (실제 dev MySQL과 분리, docker 없이도 실행 가능)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0") // 코틀린 편의 모키토 프레임워크

    // 모니터링
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // LLM (Spring AI)
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")

    // 웹소켓
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    // 캐싱
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")
}

tasks.named<Test>("test") {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}
