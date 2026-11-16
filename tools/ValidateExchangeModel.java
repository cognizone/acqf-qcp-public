import java.util.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.*;
import org.apache.jena.vocabulary.RDF;

/** Exercise the published file using standard SHACL, without QCP preprocessing. */
public class ValidateExchangeModel {
  static final String SH = "http://www.w3.org/ns/shacl#";
  static final String BASE = "https://data.acqf-qcp.africa/model/exchange-model/resource/";
  static final String QCP = "https://data.acqf-qcp.africa/model/internal/";
  static final String ELM = "http://data.europa.eu/snb/model/elm/";
  static final String ITEM = "urn:validation:item";

  public static void main(String[] args) {
    Model published = RDFDataMgr.loadModel(args[0]);
    Shapes.parse(published); // Parse the complete artifact as well as the isolated test shapes.
    boolean acqf = args[1].equals("acqf");
    Model levels = ModelFactory.createDefaultModel().add(published);
    for (String target : List.of("targetNode", "targetClass", "targetSubjectsOf", "targetObjectsOf"))
      levels.removeAll(null, levels.createProperty(SH + target), null);
    Resource propertyShape = levels.createResource(BASE + "QualificationShape-educationLevelNqf");
    Resource target = levels.createResource("urn:validation:target");
    levels.add(target, RDF.type, levels.createResource(SH + "NodeShape"));
    levels.add(target, levels.createProperty(SH + "targetNode"), levels.createResource(ITEM));
    levels.add(target, levels.createProperty(SH + "property"), propertyShape);
    var members = propertyShape.getRequiredProperty(levels.createProperty(SH + "in"))
        .getObject().as(RDFList.class).asJavaList();
    require(members.size() == (acqf ? 161 : 8), "Unexpected level count: " + members.size());
    require(propertyShape.hasProperty(levels.createProperty(QCP + "startsWith")) == acqf, "Product prefix");
    Property path = levels.createProperty(propertyShape.getRequiredProperty(levels.createProperty(SH + "path")).getResource().getURI());
    Shapes shapes = Shapes.parse(levels);
    check(shapes, path, List.of(), true);
    for (RDFNode member : members) check(shapes, path, List.of(member), true);
    check(shapes, path, members.subList(0, 2), true); // Existing NQF field has no maxCount.
    for (RDFNode invalid : List.of(levels.createResource("https://example.org/unknown"),
        levels.createResource("https://data.acqf-qcp.africa/model/unknown"), levels.createResource(),
        levels.createLiteral(members.get(0).toString()))) {
      check(shapes, path, List.of(invalid), false);
      check(shapes, path, List.of(members.get(0), invalid), false);
    }
    check(shapes, path, List.of(levels.createResource(acqf
        ? "http://data.europa.eu/snb/eqf/1"
        : "https://data.acqf-qcp.africa/model/tn-nqf-levels/resource/level-1")), false);
    Model unknown = ModelFactory.createDefaultModel();
    unknown.add(unknown.createResource(ITEM), path, unknown.createResource("https://example.org/unknown"));
    require(ShaclValidator.get().validate(shapes, unknown.getGraph()).getEntries().size() == (acqf ? 2 : 1),
        "Unknown-IRI diagnostics changed");

    Resource unique = published.createResource(BASE + "UniqueQualificationNotationShape");
    Model uniqueness = ModelFactory.createDefaultModel();
    uniqueness.add(published.listStatements(unique, null, (RDFNode) null));
    published.listObjectsOfProperty(unique, published.createProperty(SH + "sparql"))
        .forEachRemaining(query -> uniqueness.add(published.listStatements(query.asResource(), null, (RDFNode) null)));
    Shapes duplicateShapes = Shapes.parse(uniqueness);
    notation(duplicateShapes, "ABC", "ABC", false, true, false);
    notation(duplicateShapes, "ABC", "DEF", false, true, true);
    notation(duplicateShapes, "ABC", "abc", false, true, true);
    notation(duplicateShapes, "ABC", "ABC ", false, true, true);
    notation(duplicateShapes, "123", "123", true, true, false);
    notation(duplicateShapes, "ABC", "ABC", false, false, true);
    System.out.println(args[1] + ": all " + members.size() + " levels, invalid values, cardinality, diagnostics and 6 notation cases passed");
  }

  static void notation(Shapes shapes, String first, String second, boolean numeric, boolean qualification, boolean expected) {
    Model data = ModelFactory.createDefaultModel();
    Resource a = data.createResource("urn:a"), b = data.createResource("urn:b");
    data.add(a, RDF.type, data.createResource(ELM + "Qualification"));
    data.add(b, RDF.type, data.createResource(qualification ? ELM + "Qualification" : "urn:Other"));
    Resource id1 = data.createResource(), id2 = data.createResource();
    var identifier = data.createProperty("http://www.w3.org/ns/adms#identifier");
    var notation = data.createProperty("http://www.w3.org/2004/02/skos/core#notation");
    data.add(a, identifier, id1).add(b, identifier, id2);
    data.add(id1, notation, data.createLiteral(first));
    data.add(id2, notation, numeric ? data.createTypedLiteral(second, "http://www.w3.org/2001/XMLSchema#integer") : data.createLiteral(second));
    require(ShaclValidator.get().validate(shapes, data.getGraph()).conforms() == expected, "Notation: " + first + "/" + second);
  }

  static void check(Shapes shapes, Property path, List<RDFNode> values, boolean expected) {
    Model data = ModelFactory.createDefaultModel();
    values.forEach(value -> data.add(data.createResource(ITEM), path, value));
    require(ShaclValidator.get().validate(shapes, data.getGraph()).conforms() == expected, "Levels: " + values);
  }

  static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
