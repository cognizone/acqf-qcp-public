package publication

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublicationTest {
    @Test
    fun productContextChangesDestinationsWithoutChangingSourceLocations() = fixture { root, publication ->
        write(root, "products/etf/publication.json", """{"publicBaseUrl":"https://acqf.example/","basePath":"preview/etf", "title":"ETF preview"}""")
        val product = Product(root.resolve("products/etf"))
        assertEquals(root.resolve("build/site/preview/etf/model/exchange-model.ttl"), product.model(publication.siteDirectory))
        assertEquals(root.resolve("build/site/preview/etf/model/reference.html"), product.reference(publication.siteDirectory))
        assertEquals("ETF preview", product.title)
        assertEquals(root.resolve("products/etf"), product.directory)
    }

    @Test
    fun acqfLegacyReferenceRemainsRelativeToTheSiteRoot() = fixture { root, publication ->
        val product = publication.products.single()
        assertEquals(root.resolve("build/site/exchange-model/exchange-model.html"), product.reference(publication.siteDirectory))
        assertEquals("ACQF model reference", product.title)
    }

    @Test
    fun onlyPublicSourceFoldersAreCopied() = fixture { root, publication ->
        write(root, "common/model/exchange-model.ttl", "common source model")
        write(root, "common/validation-inputs/private.jsonld", "private RDF input")
        write(root, "common/templates/reference.html", "private template")
        write(root, "products/acqf/tests/contract.json", "private expectation")
        write(root, "docs/legacy.html", "frozen snapshot")
        write(root, "products/acqf/README.md", "private provenance")
        assertEquals(setOf("common/index.html", "index.html"), publication.resources().keys)
    }

    @Test
    fun twoSourcesCannotSilentlyOverwriteTheSamePublicFile() = fixture { root, publication ->
        write(root, "products/acqf/assets/index.html", "collision with product documentation")
        val error = assertFailsWith<IllegalStateException> { publication.resources() }
        assertTrue(error.message.orEmpty().contains("Duplicate site output: index.html"))
    }

    @Test
    fun sourceCannotOverwriteAGeneratedModelAndFailurePreservesPreviousSite() = fixture { root, publication ->
        write(root, "products/acqf/assets/model/exchange-model.ttl", "collision with generated model")
        write(root, "build/site/previous.html", "previous successful artifact")
        val error = assertFailsWith<IllegalStateException> { publication.buildSite() }
        assertTrue(error.message.orEmpty().contains("Duplicate static/model/reference"))
        assertEquals("previous successful artifact", root.resolve("build/site/previous.html").readText())
        assertTrue(root.resolve("build/publication").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun productContextCannotWriteOutsideTheSite() = fixture { root, publication ->
        write(root, "products/acqf/publication.json", """{"publicBaseUrl":"https://acqf.example/","basePath":"../../outside"}""")
        val product = Product(root.resolve("products/acqf"))
        assertFailsWith<IllegalArgumentException> { product.model(publication.siteDirectory) }
        assertFalse(root.resolve("outside").exists())
    }

    @Test
    fun legacyOverrideCannotWriteOutsideTheSite() = fixture { root, publication ->
        write(root, "products/acqf/publication.json", """{"publicBaseUrl":"https://acqf.example/","basePath":"", "referencePath":"../../outside.html"}""")
        val product = Product(root.resolve("products/acqf"))
        assertFailsWith<IllegalArgumentException> { product.reference(publication.siteDirectory) }
        assertFalse(root.resolve("outside.html").exists())
    }

    @Test
    fun unknownProductFailsBeforeCreatingModels() = fixture { root, publication ->
        assertFailsWith<IllegalArgumentException> { publication.selectedProducts("typo") }
        assertFalse(root.resolve("build/models").exists())
    }

    @Test
    fun failedExternalCommandRetainsItsDiagnostics() = fixture { root, _ ->
        val error = assertFailsWith<IllegalStateException> {
            Tools(root, root.resolve("unused-jena")).command("bash", "-c", "printf 'failure detail'; exit 7")
        }
        assertTrue(error.message.orEmpty().contains("Command failed (7)"))
        assertTrue(error.message.orEmpty().contains("failure detail"))
    }

    private fun fixture(test: (File, Publication) -> Unit) {
        val root = Files.createTempDirectory("publication-test-").toFile()
        try {
            write(root, "common/publication.json", """{"publicBaseUrl":"https://common.example/","paths":{"documentation":"common"}}""")
            write(root, "common/documentation/index.html", "shared guidance")
            write(root, "products/acqf/publication.json", """{"publicBaseUrl":"https://acqf.example/","basePath":"", "referencePath":"exchange-model/exchange-model.html"}""")
            write(root, "products/acqf/profile.ttl", "profile source")
            write(root, "products/acqf/documentation/index.html", "product guidance")
            test(root, Publication(root, Tools(root, root.resolve("unused-jena"))))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun write(root: File, path: String, text: String) {
        root.resolve(path).apply { parentFile.mkdirs(); writeText(text) }
    }
}
