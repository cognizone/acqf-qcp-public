package publication

import java.io.File
import java.net.URI

// The manifest carries destinations only; source folders follow repository conventions.
class Product(val directory: File) {
    val manifest = directory.resolve("publication.json")
    private val config = json(manifest)
    val name = directory.name
    val publicBaseUrl = normalizedPublicBaseUrl(requireNotNull(config.get("publicBaseUrl")) {
        "Missing publicBaseUrl: $manifest"
    }.asString)
    val basePath = requireNotNull(config.get("basePath")) { "Missing basePath: $manifest" }.asString
    val referencePath = config.get("referencePath")?.asString ?: path("model/reference.html")
    val title = config.get("title")?.asString ?: "${name.uppercase()} model reference"
    fun model(site: File) = siteFile(path("model/exchange-model.ttl"), site)
    fun reference(site: File) = siteFile(referencePath, site)
    fun path(suffix: String) = listOf(basePath, suffix).filter { it.isNotEmpty() }.joinToString("/")
}

fun normalizedPublicBaseUrl(value: String): String {
    val uri = URI.create(value)
    require(uri.scheme?.lowercase() in setOf("http", "https") && uri.host != null &&
        uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
        "publicBaseUrl must be an absolute HTTP(S) URL without credentials, query or fragment: $value"
    }
    return uri.toASCIIString().trimEnd('/') + "/"
}
