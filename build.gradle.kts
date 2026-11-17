import groovy.json.JsonSlurper
import java.nio.file.Files

plugins { base }

fun json(file: File) = JsonSlurper().parse(file) as Map<*, *>
fun Map<*, *>.text(key: String) = get(key) as String
@Suppress("UNCHECKED_CAST")
fun Map<*, *>.rows(key: String) = get(key) as List<Map<String, String>>
fun bindings(file: File) = (json(file)["results"] as Map<*, *>)["bindings"] as List<*>

val archive = file("publication/jena.sha512").readText().trim().split(Regex("\\s+"))[1]
val jenaRoot = layout.buildDirectory.dir("tools/" + archive.removeSuffix(".tar.gz"))
val jenaBin = jenaRoot.get().asFile.resolve("bin")
val products = file("products").listFiles()!!.sortedBy { it.name }
    .map { it.resolve("publication.json") }.filter { it.isFile }
val selected = providers.gradleProperty("product").orElse("all")

fun command(vararg args: String): String = providers.exec {
    commandLine(*args)
    environment("PATH", "$jenaBin:${System.getenv("PATH")}")
}.standardOutput.asText.get()
fun jena(tool: String, vararg args: String) = command(jenaBin.resolve(tool).path, *args)
fun uri(file: File) = file.toPath().toUri().toASCIIString()
fun inside(parent: File, child: File): File {
    require(child.canonicalFile.toPath().startsWith(parent.canonicalFile.toPath())) { "Path outside $parent: $child" }
    return child
}
fun output(config: File, site: File): File {
    val path = json(config).text("output")
    require(path.startsWith("docs/")) { "Output must be inside docs/: $path" }
    return inside(site, site.resolve(path.removePrefix("docs/")))
}
fun selectedProducts(): List<File> {
    val result = products.filter { selected.get() == "all" || it.parentFile.name == selected.get() }
    require(result.isNotEmpty()) { "Unknown product: ${selected.get()}" }
    return result
}
fun workspace(action: (File) -> Unit) {
    val parent = layout.buildDirectory.dir("publication").get().asFile.apply { mkdirs() }
    val work = Files.createTempDirectory(parent.toPath(), "run-").toFile()
    try { action(work) } finally { work.deleteRecursively() }
}

fun assembleModel(config: File, work: File): File {
    work.mkdirs()
    val manifest = work.resolve("manifest.ttl").apply { writeText("@prefix p: <urn:publication:graph:> .\n") }
    val dataset = mutableListOf("--data=$manifest")
    var index = 0
    fun stage(role: String, source: File) {
        inside(rootDir, source)
        require(source.isFile) { "Missing RDF input: $source" }
        val target = work.resolve("input-${index++}.ttl")
        if (source.extension == "jsonld") {
            target.writeText(jena("riot", "--quiet", "--syntax=JSONLD11", "--base=${uri(source)}", "--formatted=TURTLE", source.path))
        } else target.writeText("@base <${uri(source)}> .\n" + source.readText())
        manifest.appendText("p:$role p:source <${uri(target)}> .\n")
        dataset += "--namedGraph=${uri(target)}"
    }
    fun query(name: String, format: String): File = work.resolve("$name.${if (format == "TURTLE") "ttl" else format.lowercase()}").apply {
        writeText(jena("arq", *dataset.toTypedArray(), "--query=${file("publication/queries/$name.rq")}", "--results=$format"))
    }
    stage("common", file("common/model/exchange-model.ttl"))
    stage("profile", config.parentFile.resolve(json(config).text("profile")))
    for (directory in listOf(file("common/vocabularies"), file("common/validation-inputs"), config.parentFile.resolve("vocabularies"))) {
        require(directory.isDirectory) { "Missing vocabulary directory: $directory" }
        directory.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach { stage("vocabulary", it) }
    }
    val bound = query("bindings", "TURTLE")
    manifest.appendText("p:bindings p:source <${uri(bound)}> .\n")
    dataset += "--namedGraph=${uri(bound)}"
    val errors = bindings(query("input-errors", "JSON"))
    check(errors.isEmpty()) { "Invalid vocabulary inputs: $errors" }
    val members = query("allowed-values", "TSV").readLines().drop(1).map { it.split('\t') }
    val lists = work.resolve("lists.ttl").apply {
        writeText(members.groupBy { it[0] }.entries.joinToString("\n") { (shape, rows) ->
            "$shape <http://www.w3.org/ns/shacl#in> (${rows.joinToString(" ") { it[1] }}) ."
        })
    }
    val structure = query("export-structure", "TURTLE")
    return work.resolve("generated.ttl").apply {
        writeText(jena("riot", "--quiet", "--formatted=TURTLE", structure.path, lists.path))
        jena("shacl", "parse", path)
    }
}

// These are source-to-public-URL mappings, shared by generation and checks.
fun resources(): Map<String, File> {
    val result = linkedMapOf<String, File>()
    for (config in listOf(file("common/publication.json")) + products) {
        for (mapping in json(config).rows("resources")) {
            val source = inside(rootDir, config.parentFile.resolve(mapping.getValue("source")))
            require(source.exists()) { "Missing site source: $source" }
            for (input in source.walkTopDown().filter { it.isFile }) {
                val suffix = if (source.isDirectory) input.relativeTo(source).path else ""
                val target = inside(file("docs"), file("docs").resolve(mapping.getValue("target")).resolve(suffix))
                val relative = target.relativeTo(file("docs")).path
                check(result.put(relative, input) == null) { "Duplicate site output: $relative" }
            }
        }
    }
    return result
}
fun buildModels(configs: List<File>, site: File, work: File) {
    // Generate every graph before replacing any published graph.
    val generated = configs.associateWith { assembleModel(it, work.resolve(it.parentFile.name)) }
    for ((config, model) in generated) {
        val target = output(config, site).apply { parentFile.mkdirs() }
        val temporary = Files.createTempFile(target.parentFile.toPath(), "model-", ".ttl")
        try {
            temporary.toFile().writeText("# Generated by publication/publish.sh build; edit model, profile or vocabulary sources.\n" + model.readText())
            check(temporary.toFile().setReadable(true, false)) { "Cannot make model readable: $temporary" }
            Files.move(temporary, target.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }
}
fun checkModels(configs: List<File>, work: File) {
    for (config in configs) {
        val folder = work.resolve(config.parentFile.name)
        val model = assembleModel(config, folder)
        jena("rdfcompare", model.path, output(config, file("docs")).path, "TURTLE", "TURTLE")
        logger.lifecycle(command("bash", "publication/tests/check.sh", rootDir.path, config.path, folder.path).trim())
    }
}

val setupJena = tasks.register<Exec>("setupJena") {
    inputs.file("publication/jena.sha512")
    outputs.dir(jenaRoot)
    commandLine("bash", "publication/setup-jena.sh", layout.buildDirectory.dir("tools").get().asFile)
}
tasks.register("buildModels") {
    dependsOn(setupJena)
    doLast { workspace { buildModels(selectedProducts(), file("docs"), it) } }
}
tasks.register("checkModels") {
    dependsOn(setupJena)
    doLast { workspace { checkModels(selectedProducts(), it) } }
}
tasks.register("buildValidation") {
    group = "publication"
    dependsOn(setupJena)
    doLast {
        workspace { work ->
            val site = work.resolve("docs").apply { mkdirs() }
            resources().forEach { (path, source) -> site.resolve(path).apply { parentFile.mkdirs() }.writeBytes(source.readBytes()) }
            buildModels(products, site, work)
            site.copyRecursively(file("docs"), overwrite = true)
        }
    }
}
val checkValidation = tasks.register("checkValidation") {
    group = "verification"
    dependsOn(setupJena)
    mustRunAfter("buildValidation")
    doLast {
        val resources = resources()
        resources.forEach { (path, source) ->
            check(file("docs/$path").readBytes().contentEquals(source.readBytes())) { "Stale website resource: docs/$path" }
        }
        for (config in products) {
            val paths = config.parentFile.resolve(json(config).text("tests")).resolve("published-paths.txt")
            if (paths.isFile) paths.readLines().forEach { check(file(it).isFile) { "Missing established public path: $it" } }
        }
        workspace { work ->
            logger.lifecycle(command("bash", "publication/tests/check-inputs.sh", rootDir.path, work.path).trim())
            checkModels(products, work)
        }
        val expected = resources.keys.map { "docs/$it" }.toSet() + products.map { json(it).text("output") }
        val obsolete = command("git", "ls-files", "docs").lineSequence().filter { it.isNotEmpty() }.toSet() - expected
        check(obsolete.isEmpty()) { "Obsolete tracked site files: $obsolete" }
        logger.lifecycle("Checked website resources and both product contracts")
    }
}
tasks.named("check") { dependsOn(checkValidation) }
tasks.register("test") { dependsOn(checkValidation) }

// The HTML owns display order and placeholders; vocabulary RDF owns the titles.
tasks.register("generateNqfList") {
    group = "documentation"
    description = "Refresh vocabulary titles in the ACQF landing page's NQF list."
    dependsOn(setupJena)
    doLast {
        val index = file("products/acqf/documentation/index.html")
        val html = index.readText()
        val block = Regex("(?s)<!-- BEGIN GENERATED NQF LEVEL LIST -->\\n(.*?)\\n\\s*<!-- END GENERATED NQF LEVEL LIST -->")
        val match = block.findAll(html).toList().single().groups[1]!!
        workspace { work ->
            val data = file("products/acqf/vocabularies").walkTopDown().filter { it.isFile }.sortedBy { it.path }
                .mapIndexed { i, source ->
                    val input = work.resolve("vocabulary-$i.ttl")
                    input.writeText("@base <${uri(source)}> .\n" + source.readText())
                    "--data=$input"
                }.toList()
            val result = work.resolve("titles.json")
            result.writeText(jena("arq", *data.toTypedArray(), "--query=${file("publication/queries/nqf-titles.rq")}", "--results=JSON"))
            val titles = bindings(result).map { it as Map<*, *> }
                .groupBy { (it["scheme"] as Map<*, *>).text("value") }
                .mapValues { (_, rows) -> (rows.first()["title"] as Map<*, *>).text("value") }
            val anchor = Regex("(<a href=\"model/([^\"]+)\">).*?(</a>)")
            val list = anchor.replace(match.value) { link ->
                val raw = titles.getValue("https://data.acqf-qcp.africa/model/${link.groupValues[2]}")
                    .removeSuffix(" levels and level descriptors")
                val title = if (raw == "NQF") "NQF levels" else if ("levels" in raw.lowercase()) raw else "$raw levels"
                val escaped = title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                link.groupValues[1] + escaped + link.groupValues[3]
            }
            index.writeText(html.replaceRange(match.range, list))
        }
    }
}
