import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.plugins.JavaPluginExtension

// Applied by both publishers; ETF supplies its pinned common checkout.
val commonRoot: File by project.extra
val product: String by project.extra
repositories { mavenCentral() }
configure<JavaPluginExtension> { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
val sourceSets = extensions.getByType<SourceSetContainer>()
sourceSets.named("main") { java.setSrcDirs(listOf(commonRoot.resolve("src/main/java"))) }
sourceSets.named("test") { java.setSrcDirs(listOf(commonRoot.resolve("src/test/java"))) }
dependencies {
    add("implementation", "org.apache.jena:jena-shacl:5.6.0")
    add("testImplementation", platform("org.junit:junit-bom:5.12.2"))
    add("testImplementation", "org.junit.jupiter:junit-jupiter")
    add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
}
listOf("buildValidation" to "build", "checkValidation" to "check").forEach { (taskName, operation) ->
    tasks.register<JavaExec>(taskName) {
        group = if (operation == "build") "publication" else "verification"
        classpath = sourceSets.named("main").get().runtimeClasspath
        mainClass.set("zone.cogni.publication.ExchangeModel")
        args(rootDir.absolutePath, commonRoot.absolutePath, operation)
        if (operation == "check") mustRunAfter("buildValidation")
    }
}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("productRoot", rootDir.absolutePath)
    systemProperty("commonRoot", commonRoot.absolutePath)
    systemProperty("product", product)
    mustRunAfter("buildValidation")
}
tasks.named("check") { dependsOn("checkValidation") }

tasks.withType<Test>().configureEach {
    inputs.dir(rootDir.resolve("docs/model"))
    inputs.dir(rootDir.resolve("publication"))
    inputs.dir(commonRoot.resolve("docs/model"))
    inputs.dir(commonRoot.resolve("publication"))
}
