plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")

  id("otel.jmh-conventions")
  id("otel.animalsniffer-conventions")
}

description = "OpenTelemetry Log SDK"
otelJava.moduleName.set("io.opentelemetry.sdk.logs")

sourceSets {
  create("java9") {
    java {
      srcDirs("src/main/java9")
    }
    compileClasspath += sourceSets.main.get().compileClasspath
  }
}

tasks {
  named<JavaCompile>("compileJava9Java") {
    options.release = 9
  }

  jar {
    into("META-INF/versions/9") {
      from(sourceSets["java9"].output)
    }
    manifest.attributes(
      "Multi-Release" to "true"
    )
  }

  // Configure JMH jar to inherit multi-release structure from main jar
  named<Jar>("jmhJar") {
    val mainJar = jar.get()
    dependsOn(mainJar)

    // Inherit manifest attributes and multi-release structure
    manifest.attributes(mainJar.manifest.attributes)
    from(sourceSets["java9"].output) {
      into("META-INF/versions/9")
    }
  }
}

dependencies {
  api(project(":api:all"))
  api(project(":sdk:common"))
  compileOnly(project(":api:incubator"))

  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":sdk:testing"))

  testImplementation("org.awaitility:awaitility")
  testImplementation("com.google.guava:guava")
}

testing {
  suites {
    register<JvmTestSuite>("testIncubating") {
      dependencies {
        implementation(project(":sdk:testing"))
        implementation(project(":api:incubator"))
        implementation("io.opentelemetry.semconv:opentelemetry-semconv-incubating")
        implementation("com.google.guava:guava")
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
