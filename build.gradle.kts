import java.util.Base64

plugins {
    id("java")
    id("maven-publish")
    id("signing")
    id("com.gradleup.nmcp") version "0.0.9"
    id("com.diffplug.spotless") version "8.2.1"
}

group = "io.github.nanamochi"
version = "0.0.1"

repositories {
    mavenCentral()
}

val lombokVersion = "1.18.46"

dependencies {
    implementation("io.sigpipe:jbsdiff:1.0")
    compileOnly("org.projectlombok:lombok:${lombokVersion}")
    annotationProcessor("org.projectlombok:lombok:${lombokVersion}")
    testCompileOnly("org.projectlombok:lombok:${lombokVersion}")
    testAnnotationProcessor("org.projectlombok:lombok:${lombokVersion}")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

val generateJavadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.javadoc)
    archiveClassifier.convention("javadoc")
    from(tasks.javadoc.map { it.destinationDir })
}

val generateSourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.convention("sources")
    from(sourceSets.main.map { it.allJava })
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(generateSourcesJar)
            artifact(generateJavadocJar)
            pom {
                name = "osz2.jar"
                description = "Java library for reading & writing osz2 files."
                url = "https://github.com/7mochi/osz2.jar"

                licenses {
                    license {
                        name = "MIT License"
                        url = "https://github.com/7mochi/osz2.jar/blob/master/LICENSE"
                    }
                }

                developers {
                    developer {
                        id = "7mochi"
                        email = "flyingcatdm@gmail.com"
                    }
                }

                scm {
                    url = "https://github.com/7mochi/osz2.jar"
                }
            }
        }
    }
    repositories {
        mavenLocal()
    }
}

signing {
    val signingKeyBase64 = providers.environmentVariable("GPG_SIGNING_KEY")
    val signingPassphrase = providers.environmentVariable("GPG_SIGNING_PASSPHRASE")

    if (signingKeyBase64.isPresent && signingPassphrase.isPresent) {
        val signingKey = Base64.getDecoder()
            .decode(signingKeyBase64.get())
            .toString(Charsets.UTF_8)

        useInMemoryPgpKeys(signingKey, signingPassphrase.get())
        sign(publishing.publications["mavenJava"])
    }
}

nmcp {
    publishAllPublications {
        publicationType = "USER_MANAGED"

        val remoteUsername = providers.environmentVariable("SONATYPE_USERNAME")
        val remotePassword = providers.environmentVariable("SONATYPE_PASSWORD")

        if (remoteUsername.isPresent && remotePassword.isPresent) {
            username.set(remoteUsername.get())
            password.set(remotePassword.get())
        }
    }
}

spotless {
    format("misc") {
        target(".gitignore", "*.md")
        trimTrailingWhitespace()
        endWithNewline()
        leadingTabsToSpaces(4)
    }

    java {
        target(
            "src/main/java/io/github/nanamochi/osz2/**/*.java",
            "src/test/java/io/github/nanamochi/osz2/**/*.java"
        )
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
        palantirJavaFormat("2.94.0")
        importOrder()
        formatAnnotations()
    }
}