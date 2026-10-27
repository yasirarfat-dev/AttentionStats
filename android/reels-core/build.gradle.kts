plugins { kotlin("jvm") }
kotlin {
    jvmToolchain(17)
    compilerOptions { allWarningsAsErrors.set(true) }
}
sourceSets {
    main { kotlin.srcDir("../../native/reels-core/src/main/kotlin") }
    test { kotlin.srcDir("../../native/reels-core/src/test/kotlin") }
}
tasks.register<JavaExec>("verifyReels") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.attentionstats.reels.ReelsTestsKt")
}
tasks.check { dependsOn("verifyReels") }
