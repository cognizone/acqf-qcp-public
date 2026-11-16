plugins { base }

// Preserve the existing ACQF commands; the RDF implementation lives in Jena CLI.
val archive = file("publication/jena.sha512").readText().trim().split(Regex("\\s+"))[1]
val jenaRoot = layout.buildDirectory.dir("tools/" + archive.removeSuffix(".tar.gz"))
val setupJena = tasks.register<Exec>("setupJena") {
    inputs.file("publication/jena.sha512")
    outputs.dir(jenaRoot)
    commandLine("bash", "publication/setup-jena.sh", layout.buildDirectory.dir("tools").get().asFile)
}
listOf("buildValidation" to "build", "checkValidation" to "check").forEach { (name, operation) ->
    tasks.register<Exec>(name) {
        group = if (operation == "build") "publication" else "verification"
        dependsOn(setupJena)
        environment("PATH", "${jenaRoot.get().asFile}/bin:${System.getenv("PATH")}")
        commandLine("bash", "publication/site.sh", operation)
        if (operation == "check") mustRunAfter("buildValidation")
    }
}
tasks.named("check") { dependsOn("checkValidation") }
tasks.register("test") { dependsOn("checkValidation") }
