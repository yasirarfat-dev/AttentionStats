plugins { kotlin("jvm") }
kotlin {
    jvmToolchain(17)
    compilerOptions { allWarningsAsErrors.set(true) }
}
sourceSets {
    main { kotlin.srcDir("../../native/protection-core/src/main/kotlin") }
    test { kotlin.srcDir("../../native/protection-core/src/test/kotlin") }
}
tasks.register<JavaExec>("verifyProtection") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.attentionstats.protection.ScheduleTestsKt")
}
tasks.check { dependsOn("verifyProtection") }
