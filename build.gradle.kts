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
fun siteFile(path: String, site: File): File {
    require(path.startsWith("docs/")) { "Output must be inside docs/: $path" }
    return inside(site, site.resolve(path.removePrefix("docs/")))
}
fun output(config: File, site: File) = siteFile(json(config).text("output"), site)
fun referenceOutput(config: File, site: File) = siteFile((json(config)["reference"] as Map<*, *>).text("output"), site)

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
fun checkModels(configs: List<File>, work: File, site: File? = null) {
    for (config in configs) {
        val folder = work.resolve(config.parentFile.name).apply { mkdirs() }
        val model = if (site == null) assembleModel(config, folder)
            else output(config, site).copyTo(folder.resolve("generated.ttl"), overwrite = true)
        jena("rdfcompare", model.path, output(config, file("docs")).path, "TURTLE", "TURTLE")
        logger.lifecycle(command("bash", "publication/tests/check.sh", rootDir.path, config.path, folder.path).trim())
    }
}

// Documentation reads assembled graphs and writes only generated outputs.
fun html(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
fun term(row: Map<*, *>, key: String) = (row[key] as? Map<*, *>)?.get("value")?.toString().orEmpty()
val referencePrefixes = linkedMapOf(
    "dc" to "http://purl.org/dc/terms/", "elm" to "http://data.europa.eu/snb/model/elm/",
    "adms" to "http://www.w3.org/ns/adms#", "foaf" to "http://xmlns.com/foaf/0.1/",
    "rdf" to "http://www.w3.org/1999/02/22-rdf-syntax-ns#", "sh" to "http://www.w3.org/ns/shacl#",
    "skos" to "http://www.w3.org/2004/02/skos/core#", "xsd" to "http://www.w3.org/2001/XMLSchema#",
    "acqf-qcp-model" to "https://data.acqf-qcp.africa/model/", "owl" to "http://www.w3.org/2002/07/owl#"
)
fun compact(iri: String): String {
    val prefix = referencePrefixes.entries.firstOrNull { iri.startsWith(it.value) }
    return if (prefix == null) iri else "${prefix.key}:${iri.removePrefix(prefix.value)}"
}
fun generateReference(config: File, site: File, work: File) {
    work.mkdirs()
    val model = output(config, site)
    val result = work.resolve("reference.json")
    result.writeText(jena("arq", "--data=$model", "--query=${file("publication/queries/model-reference.rq")}", "--results=JSON"))
    val rows = bindings(result).map { it as Map<*, *> }
    val sections = rows.groupBy { term(it, "class") }.toSortedMap().map { (type, classRows) ->
        val id = compact(type)
        val description = classRows.filter { term(it, "path").isEmpty() }.map { term(it, "description") }.firstOrNull { it.isNotEmpty() }.orEmpty()
        val fields = classRows.groupBy { term(it, "shape") }.values.joinToString("\n") { field ->
            val first = field.first()
            val path = term(first, "path")
            val structured = (first["path"] as? Map<*, *>)?.get("type") == "bnode"
            val fieldId = if (path.isEmpty()) "${id}_rules_${term(first, "shape").substringAfterLast('/')}" else "${id}_${if (structured) "null" else compact(path)}"
            val name = term(first, "name").ifEmpty { if (path.isEmpty()) "Class rules" else compact(term(first, "inverse").ifEmpty { path }) }
            val constraints = field.filter { term(it, "constraint").isNotEmpty() }.groupBy { term(it, "constraint") }.toSortedMap().map { (constraint, values) ->
                val rendered = values.map { row ->
                    val value = term(row, "value")
                    when ((row["value"] as? Map<*, *>)?.get("type")) {
                        "uri" -> "<a href=\"${html(value)}\"><code>${html(compact(value))}</code></a>"
                        "bnode" -> "Structured constraint; see complete SHACL model"
                        else -> html(value)
                    }
                }.distinct().sorted()
                if (constraint == "http://www.w3.org/ns/shacl#in") "<details><summary>${rendered.size} permitted values</summary><ul>${rendered.joinToString("") { "<li>$it</li>" }}</ul></details>"
                else "<div><code>${html(compact(constraint))}</code>: ${rendered.joinToString(", ")}</div>"
            }.joinToString("\n")
            val displayPath = if (structured) "inverse " + compact(term(first, "inverse")) else compact(path)
            "<tr id=\"${html(fieldId)}\"><td><b>${html(name)}</b><p>${html(term(first, "description"))}</p></td><td><code>${html(displayPath)}</code></td><td>$constraints</td></tr>"
        }
        "<section id=\"${html(id)}\"><h2>${html(id)}</h2><p>${html(description)}</p><table><thead><tr><th>Field</th><th>Path</th><th>Constraints</th></tr></thead><tbody>$fields</tbody></table></section>"
    }.joinToString("\n")
    val reference = referenceOutput(config, site)
    val documentTarget = json(config).rows("resources").single { it["source"] == "documentation" }.getValue("target")
    val homepage = site.resolve(documentTarget).resolve("index.html")
    val rendered = file("common/templates/model-reference.html").readText()
        .replace("{{title}}", html((json(config)["reference"] as Map<*, *>).text("title")))
        .replace("{{home}}", html(homepage.relativeTo(reference.parentFile).invariantSeparatorsPath))
        .replace("{{model}}", html(model.relativeTo(reference.parentFile).invariantSeparatorsPath))
        .replace("{{prefixes}}", "<details><summary>Prefixes</summary>" + referencePrefixes.entries.joinToString("") { "<div><code>${it.key}</code>: ${html(it.value)}</div>" } + "</details>")
        .replace("{{content}}", sections)
    reference.parentFile.mkdirs()
    reference.writeText(rendered)
}
fun checkCatalogueLinks(config: File, work: File) {
    work.mkdirs()
    val profile = work.resolve("profile.ttl").apply { writeText(config.parentFile.resolve(json(config).text("profile")).readText()) }
    val sources = config.parentFile.resolve("vocabularies").walkTopDown().filter { it.isFile }.sortedBy { it.path }.toList()
    val graphs = sources.mapIndexed { index, source ->
        val graph = work.resolve("vocabulary-$index.ttl").apply { writeText("@base <${uri(source)}> .\n" + source.readText()) }
        graph to source
    }
    val dataset = listOf("--data=$profile") + graphs.flatMap { (graph, _) -> listOf("--data=$graph", "--namedGraph=${uri(graph)}") }
    val result = work.resolve("links.json")
    result.writeText(jena("arq", *dataset.toTypedArray(), "--query=${file("publication/queries/catalogue-links.rq")}", "--results=JSON"))
    val byGraph = graphs.associate { (graph, source) -> uri(graph) to source.canonicalFile }
    val resources = resources()
    val index = config.parentFile.resolve("documentation/index.html").canonicalFile
    val indexPath = resources.entries.single { it.value.canonicalFile == index }.key
    val base = java.net.URI("https://publication.invalid/$indexPath")
    val linked = Regex("href=\"([^\"]+)\"").findAll(index.readText()).map { base.resolve(it.groupValues[1]) }
        .filter { it.host == base.host }.mapNotNull { resources[it.path.removePrefix("/")]?.canonicalFile }.toSet()
    for (row in bindings(result).map { it as Map<*, *> }) {
        check(byGraph[term(row, "graph")] in linked) { "Catalogue scheme missing from ${index.path}: ${term(row, "scheme")}" }
    }
    logger.lifecycle("Checked catalogue documentation links for ${config.parentFile.name}")
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
val siteDirectory = layout.buildDirectory.dir("site")
val buildSite = tasks.register("buildSite") {
    group = "publication"
    description = "Build the complete website from source into a clean build/site directory."
    dependsOn(setupJena)
    doLast {
        workspace { work ->
            val stage = work.resolve("site").apply { mkdirs() }
            val resources = resources()
            val targets = resources.keys.toList() + products.flatMap { listOf(output(it, stage), referenceOutput(it, stage)).map { target -> target.relativeTo(stage).path } }
            check(targets.size == targets.toSet().size) { "Duplicate static/model/reference publication targets" }
            resources.forEach { (path, source) -> stage.resolve(path).apply { parentFile.mkdirs() }.writeBytes(source.readBytes()) }
            buildModels(products, stage, work)
            products.forEach { generateReference(it, stage, work.resolve("reference-${it.parentFile.name}")) }
            val actual = stage.walkTopDown().filter { it.isFile }.map { it.relativeTo(stage).path }.toSet()
            check(actual == targets.toSet()) { "Generated site differs from declared publication targets" }
            val destination = siteDirectory.get().asFile
            check(!destination.exists() || destination.deleteRecursively()) { "Cannot clean generated site: $destination" }
            stage.copyRecursively(destination)
        }
    }
}
tasks.register("buildValidation") {
    group = "publication"
    description = "Build the website and refresh the committed docs compatibility export."
    dependsOn(buildSite)
    doLast {
        val site = siteDirectory.get().asFile
        val generated = site.walkTopDown().filter { it.isFile }.map { "docs/" + it.relativeTo(site).invariantSeparatorsPath }.toSet()
        val obsolete = command("git", "ls-files", "docs").lineSequence().filter { it.isNotEmpty() && it !in generated }
        obsolete.forEach { check(!file(it).exists() || file(it).delete()) { "Cannot remove obsolete generated output: $it" } }
        site.copyRecursively(file("docs"), overwrite = true)
    }
}
val checkDocumentation = tasks.register("checkDocumentation") {
    group = "verification"
    description = "Check that product landing pages link every catalogue vocabulary."
    dependsOn(setupJena)
    doLast { workspace { work -> products.forEach { checkCatalogueLinks(it, work.resolve(it.parentFile.name)) } } }
}
val checkValidation = tasks.register("checkValidation") {
    group = "verification"
    dependsOn(buildSite, checkDocumentation)
    mustRunAfter("buildValidation")
    doLast {
        val site = siteDirectory.get().asFile
        val modelPaths = products.map { output(it, site).relativeTo(site).path }.toSet()
        val expected = site.walkTopDown().filter { it.isFile }.map { "docs/" + it.relativeTo(site).invariantSeparatorsPath }.toSet()
        for (candidate in site.walkTopDown().filter { it.isFile }) {
            val path = candidate.relativeTo(site).path
            if (path !in modelPaths) check(file("docs/$path").readBytes().contentEquals(candidate.readBytes())) { "Stale generated output: docs/$path" }
        }
        for (config in products) {
            val tests = config.parentFile.resolve(json(config).text("tests"))
            val paths = tests.resolve("published-paths.txt")
            if (paths.isFile) paths.readLines().forEach { check(file(it).isFile) { "Missing established public path: $it" } }
            val anchors = tests.resolve("reference-anchors.txt")
            if (anchors.isFile) {
                val html = referenceOutput(config, site).readText()
                anchors.readLines().forEach { check("id=\"$it\"" in html) { "Missing established reference anchor: $it" } }
            }
        }
        workspace { work ->
            logger.lifecycle(command("bash", "publication/tests/check-inputs.sh", rootDir.path, work.path).trim())
            checkModels(products, work, site)
        }
        val obsolete = command("git", "ls-files", "docs").lineSequence().filter { it.isNotEmpty() }.toSet() - expected
        check(obsolete.isEmpty()) { "Obsolete tracked site files: $obsolete" }
        logger.lifecycle("Checked clean site, documentation, public paths and both product contracts")
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
