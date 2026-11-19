import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java
    id("fun.bm.comfreyweight.patcher")
}

paperweight {
    filterPatches = false
    upstreams.register("lophine") {
        repo = github("LophineLabs", "Lophine")
        ref = providers.gradleProperty("lophineRef")

        patchFile {
            path = "lophine-server/build.gradle.kts"
            outputFile = file("tumble-server/build.gradle.kts")
            patchFile = file("tumble-server/build.gradle.kts.patch")
        }
        patchFile {
            path = "lophine-api/build.gradle.kts"
            outputFile = file("tumble-api/build.gradle.kts")
            patchFile = file("tumble-api/build.gradle.kts.patch")
        }
        patchRepo("paperApi") {
            upstreamPath = "paper-api"
            patchesDir = file("tumble-api/paper-patches")
            outputDir = file("paper-api")
        }
        patchDir("lophineApi") {
            upstreamPath = "lophine-api"
            excludes = listOf("build.gradle.kts", "build.gradle.kts.patch", "paper-patches", "folia-patches")
            patchesDir = file("tumble-api/lophine-patches")
            outputDir = file("lophine-api")
        }
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion = JavaLanguageVersion.of(25)
    }

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.bacteriawa.com/repository/maven-public/")
    }

    dependencies {
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.release = 25
        options.isFork = true
    }
    tasks.withType<Javadoc>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        (options as StandardJavadocDocletOptions).apply {
            addStringOption("-add-modules", "jdk.incubator.vector")
            addStringOption("Xdoclint:none", "-quiet")
        }
    }
    tasks.withType<ProcessResources>().configureEach {
        filteringCharset = Charsets.UTF_8.name()
    }
    tasks.withType<Test>().configureEach {
        testLogging {
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
            events(TestLogEvent.STANDARD_OUT)
        }
    }
}
