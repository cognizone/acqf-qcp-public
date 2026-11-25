package publication

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

class Tools(private val root: File, private val jenaBin: File) {
    fun stageRdf(source: File, target: File): File {
        inside(root, source)
        require(source.isFile) { "Missing RDF input: $source" }
        val turtle = if (source.extension == "jsonld") {
            jena("riot", "--quiet", "--syntax=JSONLD11", "--base=${uri(source)}", "--formatted=TURTLE", source.path)
        } else "@base <${uri(source)}> .\n" + source.readText()
        return target.apply { writeText(turtle) }
    }

    fun jena(tool: String, vararg args: String) = command(jenaBin.resolve(tool).path, *args)
    fun command(vararg args: String): String {
        val process = ProcessBuilder(*args).directory(root).redirectError(ProcessBuilder.Redirect.INHERIT)
            .apply { environment()["PATH"] = "$jenaBin:${System.getenv("PATH")}" }.start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        check(exitCode == 0) { "Command failed ($exitCode): ${args.joinToString(" ")}\n$output" }
        return output
    }
}

fun json(file: File): JsonObject = file.reader().use { JsonParser.parseReader(it).asJsonObject }
fun bindings(file: File): List<JsonObject> = json(file).getAsJsonObject("results")
    .getAsJsonArray("bindings").map { it.asJsonObject }
fun term(row: JsonObject, key: String) = row.getAsJsonObject(key)?.get("value")?.asString.orEmpty()
fun uri(file: File) = file.toPath().toUri().toASCIIString()
fun inside(parent: File, child: File): File {
    require(child.canonicalFile.toPath().startsWith(parent.canonicalFile.toPath())) { "Path outside $parent: $child" }
    return child
}
fun siteFile(path: String, site: File): File {
    require(path.isNotBlank()) { "Published output path must not be empty" }
    return inside(site, site.resolve(path))
}

fun rdfSources(directory: File): List<File> {
    require(directory.isDirectory) { "Missing vocabulary directory: $directory" }
    return directory.walkTopDown().filter { it.isFile }.sortedBy { it.path }.toList()
}
