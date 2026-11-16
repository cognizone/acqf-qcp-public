package zone.cogni.publication;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.apache.jena.riot.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class VocabularyPublicationTest {
    @Test void refreshPreservesTheWholeGraphAndRejectsChangedSources(@TempDir Path root) throws Exception {
        String xml = """
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                         xmlns:skos="http://www.w3.org/2004/02/skos/core#"
                         xmlns:dct="http://purl.org/dc/terms/">
                  <skos:ConceptScheme rdf:about="urn:scheme"><dct:description xml:lang="fr">Original metadata</dct:description></skos:ConceptScheme>
                  <skos:Concept rdf:about="urn:level"><skos:inScheme rdf:resource="urn:scheme"/>
                    <skos:prefLabel xml:lang="en">Level one</skos:prefLabel>
                    <dct:relation><rdf:Description><dct:title>Blank-node metadata</dct:title></rdf:Description></dct:relation>
                  </skos:Concept>
                </rdf:RDF>
                """;
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/source", exchange -> {
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/source";
            String recipe = "scheme=urn:scheme\nurl=" + url + "\nformat=RDF/XML\noutput=levels.ttl\nsource-sha256=";
            Path config = root.resolve("recipe.properties");
            Files.writeString(config, recipe + "wrong\n");
            assertThrows(IllegalStateException.class, () -> VocabularyPublication.main(new String[]{root.toString(), "recipe.properties", "refresh"}));
            assertFalse(Files.exists(root.resolve("levels.ttl")));
            Files.writeString(config, recipe + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)) + "\n");
            VocabularyPublication.main(new String[]{root.toString(), "recipe.properties", "refresh"});
            var expected = org.apache.jena.rdf.model.ModelFactory.createDefaultModel();
            RDFDataMgr.read(expected, new java.io.ByteArrayInputStream(bytes), url, Lang.RDFXML);
            assertTrue(expected.isIsomorphicWith(RDFDataMgr.loadModel(root.resolve("levels.ttl").toString())));
            byte[] before = Files.readAllBytes(root.resolve("levels.ttl"));
            Files.writeString(config, recipe + "changed\n");
            assertThrows(IllegalStateException.class, () -> VocabularyPublication.main(new String[]{root.toString(), "recipe.properties", "refresh"}));
            assertArrayEquals(before, Files.readAllBytes(root.resolve("levels.ttl")));
            VocabularyPublication.main(new String[]{root.toString(), "recipe.properties", "check"});
        } finally { server.stop(0); }
    }
}
