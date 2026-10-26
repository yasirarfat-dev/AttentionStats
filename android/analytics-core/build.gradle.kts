plugins { kotlin("jvm") }

kotlin {
    jvmToolchain(17)
    compilerOptions { allWarningsAsErrors.set(true) }
}
sourceSets {
    main { kotlin.srcDir("../../native/analytics-core/src/main/kotlin") }
    test { kotlin.srcDir("../../native/analytics-core/src/test/kotlin") }
}

tasks.register<JavaExec>("verifyAnalytics") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.attentionstats.analytics.AnalyticsTestsKt")
}
tasks.check { dependsOn("verifyAnalytics") }
