package zone.cogni.publication;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.vocabulary.SKOS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ExchangeModelTest {
    private final Path product = Path.of(System.getProperty("productRoot"));
    private final Path common = Path.of(System.getProperty("commonRoot"));
    private final String name = System.getProperty("product");
    private static final String BASELINE = "1e07dbd139a815427e6aeec8ba431adda5c25681";

    private Model historical(String path) throws Exception {
        Process process = new ProcessBuilder("git", "-C", common.toString(), "show", BASELINE + ":" + path)
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        byte[] bytes = process.getInputStream().readAllBytes();
        assertEquals(0, process.waitFor(), "Cannot read validation regression baseline");
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, new ByteArrayInputStream(bytes), Lang.TURTLE);
        return model;
    }

    @Test void acqfMatchesTheCompleteOriginalValidationGraph() throws Exception {
        assumeTrue(name.equals("acqf"));
        Model old = historical("docs/model/exchange-model.ttl");
        for (Resource shape : old.listSubjectsWithProperty(ExchangeModel.VOCABULARY).toSet()) {
            var members = new TreeSet<String>();
            for (RDFNode value : old.listObjectsOfProperty(shape, ExchangeModel.VOCABULARY).toList()) {
                String scheme = value.asResource().getURI();
                Model vocabulary = scheme.startsWith(ExchangeModel.ACQF)
                        ? historical("docs/model/" + scheme.substring(ExchangeModel.ACQF.length()))
                        : ExchangeModel.load(common.resolve("publication/isced-f.jsonld"));
                vocabulary.listSubjectsWithProperty(SKOS.inScheme, value).forEachRemaining(member -> members.add(member.getURI()));
            }
            old.add(shape, ExchangeModel.IN, old.createList(members.stream().map(old::createResource).iterator()));
            old.removeAll(shape, ExchangeModel.VOCABULARY, null);
        }
        assertTrue(old.isIsomorphicWith(new ExchangeModel(product, common).build()),
                "ACQF constraints or vocabulary membership changed");
    }

    @Test void exportMatchesSourcesAndAssemblyIsRepeatable() throws Exception {
        Model generated = new ExchangeModel(product, common).build();
        assertTrue(generated.isIsomorphicWith(new ExchangeModel(product, common).build()));
        assertTrue(generated.isIsomorphicWith(ExchangeModel.load(product.resolve("docs/model/exchange-model.ttl"))));
        assertFalse(generated.contains(null, ExchangeModel.VOCABULARY));
        assertFalse(generated.contains(null, ExchangeModel.CATALOGUE));
    }

    @Test void publishedModelValidatesWithoutApplicationPreprocessing() {
        ValidateExchangeModel.main(new String[]{product.resolve("docs/model/exchange-model.ttl").toString(), name});
    }

    @Test void missingOrEmptyCatalogueFails(@TempDir Path root) throws Exception {
        for (String contents : List.of("", """
                @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
                <https://data.acqf-qcp.africa/model/internal/virtualspace-nqfs> a skos:ConceptScheme .
                """)) {
            Files.createDirectories(root.resolve("publication"));
            Files.copy(common.resolve("docs/model/acqf-profile.ttl"), root.resolve("profile.ttl"), StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(root.resolve("empty.ttl"), contents);
            Files.writeString(root.resolve("publication/validation.json"), """
                    {"profile":"profile.ttl","vocabularies":{
                    "https://data.acqf-qcp.africa/model/internal/virtualspace-nqfs":"empty.ttl"}}
                    """);
            assertThrows(IllegalArgumentException.class, () -> new ExchangeModel(root, common).build());
        }
    }
}
