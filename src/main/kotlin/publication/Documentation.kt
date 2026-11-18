package publication

import java.io.File

// Only HTML publication links are expanded; RDF and JSON-LD sources keep their identifiers.
fun Publication.renderDocumentation(document: String) = Regex("\\{\\{([^{}]+)}}").replace(document) { match ->
    html(requireNotNull(publicBaseUrls[match.groupValues[1]]) {
        "Unknown publication URL placeholder: ${match.value}"
    })
}

fun Publication.checkCatalogueLinks(config: Product, work: File) {
    work.mkdirs()
    val profile = work.resolve("profile.ttl").apply { writeText(config.directory.resolve("profile.ttl").readText()) }
    val sources = rdfSources(config.directory.resolve("vocabularies"))
    val graphs = sources.mapIndexed { index, source ->
        val graph = tools.stageRdf(source, work.resolve("vocabulary-$index.ttl"))
        graph to source
    }
    val dataset = listOf("--data=$profile") + graphs.flatMap { (graph, _) -> listOf("--data=$graph", "--namedGraph=${uri(graph)}") }
    val result = work.resolve("links.json")
    result.writeText(tools.jena("arq", *dataset.toTypedArray(), "--query=${file("publication/queries/catalogue-links.rq")}", "--results=JSON"))
    val byGraph = graphs.associate { (graph, source) -> uri(graph) to source.canonicalFile }
    val resources = resources()
    val index = config.directory.resolve("documentation/index.html").canonicalFile
    val indexPath = resources.entries.single { it.value.canonicalFile == index }.key
    val base = java.net.URI("https://publication.invalid/$indexPath")
    val linked = Regex("href=\"([^\"]+)\"").findAll(renderDocumentation(index.readText())).map { base.resolve(it.groupValues[1]) }
        .filter { it.host == base.host }.mapNotNull { resources[it.path.removePrefix("/")]?.canonicalFile }.toSet()
    for (row in bindings(result)) {
        check(byGraph[term(row, "graph")] in linked) { "Catalogue scheme missing from ${index.path}: ${term(row, "scheme")}" }
    }
    println("Checked catalogue documentation links for ${config.name}")
}

fun Publication.generateNqfList() {
    val index = file("products/acqf/documentation/index.html")
    val document = index.readText()
    val block = Regex("(?s)<!-- BEGIN GENERATED NQF LEVEL LIST -->\\n(.*?)\\n\\s*<!-- END GENERATED NQF LEVEL LIST -->")
    val match = block.findAll(document).toList().single().groups[1]!!
    workspace { work ->
        val data = rdfSources(file("products/acqf/vocabularies"))
            .mapIndexed { i, source ->
                val input = tools.stageRdf(source, work.resolve("vocabulary-$i.ttl"))
                "--data=$input"
            }.toList()
        val result = work.resolve("titles.json")
        result.writeText(tools.jena("arq", *data.toTypedArray(), "--query=${file("publication/queries/nqf-titles.rq")}", "--results=JSON"))
        val titles = bindings(result)
            .groupBy { term(it, "scheme") }
            .mapValues { (_, rows) -> term(rows.first(), "title") }
        val anchor = Regex("(<a href=\"model/([^\"]+)\">).*?(</a>)")
        val list = anchor.replace(match.value) { link ->
            val raw = titles.getValue("https://data.acqf-qcp.africa/model/${link.groupValues[2]}")
                .removeSuffix(" levels and level descriptors")
            val title = if (raw == "NQF") "NQF levels" else if ("levels" in raw.lowercase()) raw else "$raw levels"
            val escaped = html(title)
            link.groupValues[1] + escaped + link.groupValues[3]
        }
        index.writeText(document.replaceRange(match.range, list))
    }
}
