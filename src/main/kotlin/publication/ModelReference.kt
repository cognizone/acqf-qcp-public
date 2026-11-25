package publication

import com.google.gson.JsonObject
import java.io.File

fun html(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private val referencePrefixes = linkedMapOf(
    "dc" to "http://purl.org/dc/terms/", "elm" to "http://data.europa.eu/snb/model/elm/",
    "adms" to "http://www.w3.org/ns/adms#", "foaf" to "http://xmlns.com/foaf/0.1/",
    "rdf" to "http://www.w3.org/1999/02/22-rdf-syntax-ns#", "sh" to "http://www.w3.org/ns/shacl#",
    "skos" to "http://www.w3.org/2004/02/skos/core#", "xsd" to "http://www.w3.org/2001/XMLSchema#",
    "acqf-qcp-model" to "https://data.acqf-qcp.africa/model/", "owl" to "http://www.w3.org/2002/07/owl#"
)

private fun compact(iri: String): String {
    val prefix = referencePrefixes.entries.firstOrNull { iri.startsWith(it.value) }
    return if (prefix == null) iri else "${prefix.key}:${iri.removePrefix(prefix.value)}"
}

private fun renderValue(row: JsonObject): String {
    val value = term(row, "value")
    return when (row.getAsJsonObject("value")?.get("type")?.asString) {
        "uri" -> "<a href=\"${html(value)}\"><code>${html(compact(value))}</code></a>"
        "bnode" -> "Structured constraint; see complete SHACL model"
        else -> html(value)
    }
}

private fun renderConstraints(rows: List<JsonObject>) = rows.filter { term(it, "constraint").isNotEmpty() }
    .groupBy { term(it, "constraint") }.toSortedMap().map { (constraint, values) ->
        val rendered = values.map(::renderValue).distinct().sorted()
        if (constraint == "http://www.w3.org/ns/shacl#in") {
            "<details><summary>${rendered.size} permitted values</summary><ul>${rendered.joinToString("") { "<li>$it</li>" }}</ul></details>"
        } else "<div><code>${html(compact(constraint))}</code>: ${rendered.joinToString(", ")}</div>"
    }.joinToString("\n")

private fun renderField(classId: String, rows: List<JsonObject>): String {
    val first = rows.first()
    val path = term(first, "path")
    val structured = first.getAsJsonObject("path")?.get("type")?.asString == "bnode"
    // Keep legacy ACQF fragments, including the established "null" suffix for inverse paths.
    val fieldId = if (path.isEmpty()) "${classId}_rules_${term(first, "shape").substringAfterLast('/')}"
        else "${classId}_${if (structured) "null" else compact(path)}"
    val name = term(first, "name").ifEmpty {
        if (path.isEmpty()) "Class rules" else compact(term(first, "inverse").ifEmpty { path })
    }
    val displayPath = if (structured) "inverse " + compact(term(first, "inverse")) else compact(path)
    return "<tr id=\"${html(fieldId)}\"><td><b>${html(name)}</b><p>${html(term(first, "description"))}</p></td><td><code>${html(displayPath)}</code></td><td>${renderConstraints(rows)}</td></tr>"
}

private fun renderSection(type: String, rows: List<JsonObject>): String {
    val id = compact(type)
    val description = rows.filter { term(it, "path").isEmpty() }.map { term(it, "description") }
        .firstOrNull { it.isNotEmpty() }.orEmpty()
    val fields = rows.groupBy { term(it, "shape") }.values.joinToString("\n") { renderField(id, it) }
    return "<section id=\"${html(id)}\"><h2>${html(id)}</h2><p>${html(description)}</p><table><thead><tr><th>Field</th><th>Path</th><th>Constraints</th></tr></thead><tbody>$fields</tbody></table></section>"
}

fun Publication.generateReference(product: Product, site: File, work: File) {
    work.mkdirs()
    val model = product.model(site)
    val result = work.resolve("reference.json")
    result.writeText(tools.jena("arq", "--data=$model", "--query=${file("publication/queries/model-reference.rq")}", "--results=JSON"))
    val sections = bindings(result).groupBy { term(it, "class") }.toSortedMap()
        .map { (type, rows) -> renderSection(type, rows) }.joinToString("\n")
    val reference = product.reference(site)
    val homepage = siteFile(product.path("index.html"), site)
    val rendered = file("common/templates/model-reference.html").readText()
        .replace("{{title}}", html(product.title))
        .replace("{{home}}", html(homepage.relativeTo(reference.parentFile).invariantSeparatorsPath))
        .replace("{{model}}", html(model.relativeTo(reference.parentFile).invariantSeparatorsPath))
        .replace("{{prefixes}}", "<details><summary>Prefixes</summary>" + referencePrefixes.entries.joinToString("") { "<div><code>${it.key}</code>: ${html(it.value)}</div>" } + "</details>")
        .replace("{{content}}", sections)
    reference.parentFile.mkdirs()
    reference.writeText(rendered)
}
