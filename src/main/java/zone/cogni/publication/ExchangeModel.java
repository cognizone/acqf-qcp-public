package zone.cogni.publication;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.jena.atlas.json.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.vocabulary.*;

/** Assemble reviewed local RDF inputs; never follow owl:imports or vocabulary URLs. */
public final class ExchangeModel {
    static final String ACQF = "https://data.acqf-qcp.africa/model/";
    static final String ETF = "https://data.etf-qd.cogni.zone/model/";
    static final Property VOCABULARY = ResourceFactory.createProperty(ACQF + "internal/controlledVocabulary");
    static final Property CATALOGUE = ResourceFactory.createProperty(ACQF + "internal/controlledVocabularyCatalogue");
    static final Property STARTS_WITH = ResourceFactory.createProperty(ACQF + "internal/startsWith");
    static final Property IN = ResourceFactory.createProperty("http://www.w3.org/ns/shacl#in");
    static final Resource PROPERTY_SHAPE = ResourceFactory.createResource("http://www.w3.org/ns/shacl#PropertyShape");

    private final Path productRoot;
    private final Path commonRoot;
    private final JsonObject config;
    private final JsonObject overrides;
    private final Map<Resource, Model> vocabularies = new HashMap<>();

    ExchangeModel(Path productRoot, Path commonRoot) throws IOException {
        this.productRoot = productRoot.toRealPath();
        this.commonRoot = commonRoot.toRealPath();
        config = JSON.read(localFile(this.productRoot, "publication/validation.json").toString());
        overrides = JSON.read(localFile(this.commonRoot, "publication/common-vocabularies.json").toString());
    }

    static Path localFile(Path root, String relative) throws IOException {
        Path path = root.resolve(relative).toRealPath();
        if (!path.startsWith(root.toRealPath()) || !Files.isRegularFile(path))
            throw new IllegalArgumentException("Invalid local input: " + path);
        return path;
    }

    static Model load(Path path) {
        return RDFDataMgr.loadModel(path.toString(), path.toString().endsWith(".jsonld") ? Lang.JSONLD11 : Lang.TURTLE);
    }

    Model vocabulary(Resource scheme) throws IOException {
        if (!scheme.isURIResource()) throw new IllegalArgumentException("Expected vocabulary IRI: " + scheme);
        if (vocabularies.containsKey(scheme)) return vocabularies.get(scheme);
        String iri = scheme.getURI();
        JsonObject productMappings = config.getObj("vocabularies");
        Path path;
        if (productMappings.hasKey(iri)) path = localFile(productRoot, productMappings.getString(iri));
        else if (overrides.hasKey(iri)) path = localFile(commonRoot, overrides.getString(iri));
        else if (iri.startsWith(ACQF)) path = localFile(commonRoot, "docs/model/" + iri.substring(ACQF.length()));
        else if (iri.startsWith(ETF)) path = localFile(productRoot, "docs/model/" + iri.substring(ETF.length()));
        else throw new IllegalArgumentException("No reviewed local vocabulary for " + iri);
        Model model = load(path);
        if (!model.contains(scheme, RDF.type, SKOS.ConceptScheme))
            throw new IllegalArgumentException("Expected ConceptScheme " + scheme + " in " + path);
        vocabularies.put(scheme, model);
        return model;
    }

    Model build() throws IOException {
        Model shapes = load(localFile(commonRoot, "docs/model/common/exchange-model.ttl"));
        Model profile = load(localFile(productRoot, config.getString("profile")));
        for (Statement binding : profile.listStatements().toList()) {
            if (!shapes.contains(binding.getSubject(), RDF.type, PROPERTY_SHAPE)
                    || !Set.of(VOCABULARY, CATALOGUE, STARTS_WITH).contains(binding.getPredicate()))
                throw new IllegalArgumentException("Invalid product binding: " + binding);
        }
        shapes.add(profile);
        for (Resource shape : shapes.listSubjectsWithProperty(CATALOGUE).toSet()) {
            for (RDFNode value : shapes.listObjectsOfProperty(shape, CATALOGUE).toList()) {
                Resource catalogue = value.asResource();
                Model graph = vocabulary(catalogue);
                Set<Resource> members = graph.listSubjectsWithProperty(SKOS.inScheme, catalogue).toSet();
                if (members.isEmpty()) throw new IllegalArgumentException("Empty framework catalogue: " + catalogue);
                for (Resource member : members) {
                    var schemes = graph.listObjectsOfProperty(member, DCTerms.relation).toSet();
                    if (schemes.size() != 1 || !schemes.iterator().next().isURIResource())
                        throw new IllegalArgumentException("Expected one level scheme for " + member);
                    shapes.add(shape, VOCABULARY, schemes.iterator().next());
                }
            }
            shapes.removeAll(shape, CATALOGUE, null);
        }
        for (Resource shape : shapes.listSubjectsWithProperty(VOCABULARY).toSet()) {
            if (shapes.contains(shape, IN)) throw new IllegalArgumentException("Already has sh:in: " + shape);
            var allowed = new TreeSet<String>();
            for (RDFNode value : shapes.listObjectsOfProperty(shape, VOCABULARY).toList()) {
                Resource scheme = value.asResource();
                Set<Resource> members = vocabulary(scheme).listSubjectsWithProperty(SKOS.inScheme, scheme).toSet();
                if (members.isEmpty() || members.stream().anyMatch(member -> !member.isURIResource()))
                    throw new IllegalArgumentException("Empty or invalid membership in " + scheme);
                members.forEach(member -> allowed.add(member.getURI()));
            }
            shapes.add(shape, IN, shapes.createList(allowed.stream().map(shapes::createResource).iterator()));
            // Runtime must not add another membership constraint from an older lookup cache.
            shapes.removeAll(shape, VOCABULARY, null);
        }
        return shapes;
    }

    public static void main(String[] args) throws IOException {
        Path product = Path.of(args[0]);
        Model generated = new ExchangeModel(product, Path.of(args[1])).build();
        Path output = product.resolve("docs/model/exchange-model.ttl");
        if (args[2].equals("check")) {
            if (!generated.isIsomorphicWith(load(output)))
                throw new IllegalStateException("Stale validation export: " + output);
            System.out.println("Checked " + output);
        } else {
            Path temporary = output.resolveSibling("exchange-model.ttl.tmp");
            try (OutputStream stream = Files.newOutputStream(temporary)) {
                stream.write("# Generated by Gradle buildValidation; edit common model, profile or CV sources.\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                RDFDataMgr.write(stream, generated, RDFFormat.TURTLE_PRETTY);
            }
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            System.out.println("Generated " + output);
        }
    }
}
