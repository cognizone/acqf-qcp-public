package publication

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PublicationUrlsTest {
    @Test
    fun ownersCanUseDifferentDomainsWhileLocalLinksAndRdfIdentifiersStayIntact() = fixture { root ->
        val publication = Publication(root, Tools(root, root.resolve("unused-jena")))
        val page = """
            <a href="{{commonUrl}}model/acqf-levels">Common vocabulary</a>
            <a href="{{acqfUrl}}exchange-model/exchange-model.html">ACQF reference</a>
            <a href="{{etfUrl}}model/exchange-model.ttl">ETF model</a>
            <a href="model/ao-nqf-levels#levels">Local vocabulary</a>
            <a href="https://data.acqf-qcp.africa/model/acqf-levels/resource/1">Concept IRI</a>
        """.trimIndent()
        assertEquals("""
            <a href="https://common.example/shared/model/acqf-levels">Common vocabulary</a>
            <a href="https://acqf.example/qualifications/exchange-model/exchange-model.html">ACQF reference</a>
            <a href="https://etf.example/model/exchange-model.ttl">ETF model</a>
            <a href="model/ao-nqf-levels#levels">Local vocabulary</a>
            <a href="https://data.acqf-qcp.africa/model/acqf-levels/resource/1">Concept IRI</a>
        """.trimIndent(), publication.renderDocumentation(page))
        // The public URL does not determine the combined artifact's filesystem layout.
        assertEquals(root.resolve("build/site/etf/model/exchange-model.ttl"),
            publication.products.single { it.name == "etf" }.model(publication.siteDirectory))
    }

    @Test
    fun misspelledPublicationPlaceholderFailsInsteadOfShippingABrokenLink() = fixture { root ->
        val publication = Publication(root, Tools(root, root.resolve("unused-jena")))
        val error = assertFailsWith<IllegalArgumentException> {
            publication.renderDocumentation("<a href=\"{{unknownUrl}}\">Unknown publication</a>")
        }
        assertTrue(error.message.orEmpty().contains("Unknown publication URL placeholder"))
    }

    @Test
    fun publicBaseMustBeAWebsiteRootRatherThanAFileOrQuery() {
        for (value in listOf("relative/path", "file:///tmp/site", "https://etf.example/?preview=1", "https://etf.example/#model")) {
            assertFailsWith<IllegalArgumentException>(value) { normalizedPublicBaseUrl(value) }
        }
    }

    private fun fixture(test: (File) -> Unit) {
        val root = Files.createTempDirectory("publication-urls-test-").toFile()
        try {
            write(root, "common/publication.json", """{"publicBaseUrl":"https://common.example/shared", "paths":{}}""")
            write(root, "products/acqf/publication.json", """{"publicBaseUrl":"https://acqf.example/qualifications/", "basePath":"", "referencePath":"exchange-model/exchange-model.html"}""")
            write(root, "products/etf/publication.json", """{"publicBaseUrl":"https://etf.example/", "basePath":"etf"}""")
            test(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun write(root: File, path: String, text: String) {
        root.resolve(path).apply { parentFile.mkdirs(); writeText(text) }
    }
}
