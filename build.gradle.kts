plugins { kotlin("jvm") version "2.4.21" }

repositories { mavenCentral() }
kotlin { jvmToolchain(21) }

val archive = file("publication/jena.sha512").readText().trim().split(Regex("\\s+"))[1]
val jenaRoot = layout.buildDirectory.dir("tools/" + archive.removeSuffix(".tar.gz"))
val siteDirectory = layout.buildDirectory.dir("site")
val selectedProduct = providers.gradleProperty("product").orElse("all")

val setupJena = tasks.register<Exec>("setupJena") {
    inputs.files("publication/jena.sha512", "publication/setup-jena.sh")
    outputs.dir(jenaRoot)
    commandLine("bash", "publication/setup-jena.sh", layout.buildDirectory.dir("tools").get().asFile)
}

// Reuse Gson from the checksum-pinned Jena distribution for JSON input/output.
dependencies {
    testImplementation(kotlin("test"))
    implementation(files(fileTree(jenaRoot) { include("lib/gson-*.jar") }).builtBy(setupJena))
}

fun publicationTask(name: String, description: String) = tasks.register<JavaExec>(name) {
    group = "publication"
    this.description = description
    dependsOn(setupJena)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("publication.PublicationKt")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    args(name, rootDir.path, jenaRoot.get().dir("bin").asFile.path, selectedProduct.get())
    inputs.files("common", "products", "publication")
}

val buildSite = publicationTask("buildSite", "Build the complete website from source into a clean build/site directory.")
buildSite.configure {
    outputs.dir(siteDirectory)
    // Always recreate the site so stray files cannot leak into the preview artifact.
    outputs.upToDateWhen { false }
}
publicationTask("buildModels", "Build validation models for the selected product or all products.").configure {
    inputs.property("product", selectedProduct)
    outputs.dir(layout.buildDirectory.dir("models"))
}
publicationTask("checkModels", "Check validation models for the selected product or all products.")
val checkDocumentation = publicationTask("checkDocumentation", "Check that product landing pages link every catalogue vocabulary.")
val checkValidation = publicationTask("checkValidation", "Check product contracts and established public paths.")
checkValidation.configure {
    group = "verification"
    dependsOn(buildSite, checkDocumentation)
    inputs.dir(siteDirectory)
}
publicationTask("generateNqfList", "Refresh vocabulary titles in the ACQF landing page's NQF list.")
tasks.register("buildValidation") {
    group = "publication"
    description = "Compatibility alias for buildSite; generated files stay under build/."
    dependsOn(buildSite)
}
tasks.named("check") { dependsOn(checkValidation) }
tasks.named("test") { dependsOn(checkValidation) }
