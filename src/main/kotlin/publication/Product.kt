package publication

import java.io.File

// The manifest carries destinations only; source folders follow repository conventions.
class Product(val directory: File) {
    val manifest = directory.resolve("publication.json")
    private val config = json(manifest)
    val name = directory.name
    val basePath = requireNotNull(config.get("basePath")) { "Missing basePath: $manifest" }.asString
    val profilePath = config.get("profilePath")?.asString ?: path("model/profile.ttl")
    val referencePath = config.get("referencePath")?.asString ?: path("model/reference.html")
    val title = config.get("title")?.asString ?: "${name.uppercase()} model reference"
    fun model(site: File) = siteFile(path("model/exchange-model.ttl"), site)
    fun reference(site: File) = siteFile(referencePath, site)
    fun path(suffix: String) = listOf(basePath, suffix).filter { it.isNotEmpty() }.joinToString("/")
}
