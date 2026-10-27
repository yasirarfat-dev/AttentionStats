plugins { kotlin("jvm") }

kotlin {
    jvmToolchain(17)
    compilerOptions { allWarningsAsErrors.set(true) }
}
sourceSets {
    main { kotlin.srcDir("../../native/collection-prototype/src/main/kotlin") }
    test { kotlin.srcDir("../../native/collection-prototype/src/test/kotlin") }
}
tasks.register<JavaExec>("verifyReconciliation") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.attentionstats.collection.reconciliation.ReconciliationTestsKt")
}
tasks.check { dependsOn("verifyReconciliation") }
