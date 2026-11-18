package publication

import java.io.File

fun Publication.assembleModel(config: Product, work: File): File {
    work.mkdirs()
    val manifest = work.resolve("manifest.ttl").apply { writeText("@prefix p: <urn:publication:graph:> .\n") }
    val dataset = mutableListOf("--data=$manifest")
    var index = 0
    fun stage(role: String, source: File) {
        val target = tools.stageRdf(source, work.resolve("input-${index++}.ttl"))
        manifest.appendText("p:$role p:source <${uri(target)}> .\n")
        dataset += "--namedGraph=${uri(target)}"
    }
    fun query(name: String, format: String): File = work.resolve("$name.${if (format == "TURTLE") "ttl" else format.lowercase()}").apply {
        writeText(tools.jena("arq", *dataset.toTypedArray(), "--query=${file("publication/queries/$name.rq")}", "--results=$format"))
    }
    stage("common", file("common/model/exchange-model.ttl"))
    stage("profile", config.directory.resolve("profile.ttl"))
    for (directory in listOf(file("common/vocabularies"), file("common/validation-inputs"), config.directory.resolve("vocabularies"))) {
        rdfSources(directory).forEach { stage("vocabulary", it) }
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
        writeText(tools.jena("riot", "--quiet", "--formatted=TURTLE", structure.path, lists.path))
        tools.jena("shacl", "parse", path)
    }
}
