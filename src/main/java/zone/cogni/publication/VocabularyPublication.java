package zone.cogni.publication;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.vocabulary.*;

/** Refresh a reviewed Turtle vocabulary from its pinned original distribution. */
public final class VocabularyPublication {
    static Model read(InputStream input, Lang lang, String base) {
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, input, base, lang);
        return model;
    }

    static void checkScheme(Model model, String iri) {
        Resource scheme = model.createResource(iri);
        if (!model.contains(scheme, RDF.type, SKOS.ConceptScheme)
                || !model.contains(null, SKOS.inScheme, scheme))
            throw new IllegalStateException("Missing or empty scheme: " + iri);
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Properties recipe = new Properties();
        try (InputStream input = Files.newInputStream(root.resolve(args[1]))) { recipe.load(input); }
        Path published = root.resolve(recipe.getProperty("output"));
        String url = recipe.getProperty("url");
        if (args[2].equals("check")) {
            try (InputStream input = Files.newInputStream(published)) {
                checkScheme(read(input, Lang.TURTLE, url), recipe.getProperty("scheme"));
            }
        } else if (args[2].equals("refresh")) {
            Lang format = switch (recipe.getProperty("format")) {
                case "RDF/XML" -> Lang.RDFXML;
                case "TURTLE" -> Lang.TURTLE;
                default -> throw new IllegalArgumentException("Expected RDF/XML or TURTLE");
            };
            var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(20)).build();
            var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(45))
                    .header("Accept", format.equals(Lang.RDFXML) ? "application/rdf+xml" : "text/turtle").build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + " fetching " + url);
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(response.body()));
            if (!digest.equals(recipe.getProperty("source-sha256")))
                throw new IllegalStateException("Upstream source checksum differs; review the distribution first");
            Model source = read(new ByteArrayInputStream(response.body()), format, url);
            checkScheme(source, recipe.getProperty("scheme"));
            Path stage = root.resolve("build/publication/export.ttl");
            Files.createDirectories(stage.getParent());
            try (OutputStream output = Files.newOutputStream(stage)) {
                RDFDataMgr.write(output, source, RDFFormat.TURTLE_PRETTY);
            }
            try (InputStream input = Files.newInputStream(stage)) {
                if (!source.isIsomorphicWith(read(input, Lang.TURTLE, url)))
                    throw new IllegalStateException("Vocabulary conversion changed the RDF graph");
            }
            Files.copy(stage, published, StandardCopyOption.REPLACE_EXISTING);
        } else throw new IllegalArgumentException("Unknown operation: " + args[2]);
        System.out.println(args[2] + ": " + published);
    }
}
