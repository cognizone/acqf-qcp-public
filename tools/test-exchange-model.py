#!/usr/bin/env python3
"""Offline publisher checks. Requires rdflib; run from any directory."""
import importlib.util
import subprocess
import tempfile
import unittest
from pathlib import Path

from rdflib import BNode, Graph, RDF, URIRef
from rdflib.collection import Collection
from rdflib.compare import isomorphic

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('publisher', ROOT / 'tools/build-exchange-model.py')
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)
QCP, SKOS, SH = publisher.QCP, publisher.SKOS, publisher.SH
BASELINE = '1e07dbd139a815427e6aeec8ba431adda5c25681'
NQF = URIRef('https://data.acqf-qcp.africa/model/exchange-model/resource/QualificationShape-educationLevelNqf')


class PublicationTest(unittest.TestCase):
    def test_acqf_matches_original_validation_graph(self):
        # Baseline is the complete pre-split dev model, including Tunisia and notation uniqueness.
        def historical(path):
            content = subprocess.check_output(['git', '-C', str(ROOT), 'show', f'{BASELINE}:{path}'])
            return Graph().parse(data=content, format='turtle')
        old = historical('docs/model/exchange-model.ttl')
        for shape in set(old.subjects(QCP.controlledVocabulary, None)):
            members = set()
            for scheme in old.objects(shape, QCP.controlledVocabulary):
                if str(scheme).startswith(publisher.ACQF):
                    graph = historical('docs/model/' + str(scheme)[len(publisher.ACQF):])
                else:
                    graph = publisher.load(ROOT / 'publication/isced-f.jsonld')
                members.update(graph.subjects(SKOS.inScheme, scheme))
            head = BNode()
            Collection(old, head, sorted(members, key=str))
            old.add((shape, SH['in'], head))
            old.remove((shape, QCP.controlledVocabulary, None))
        new = publisher.build(ROOT, ROOT)
        self.assertTrue(isomorphic(old, new), 'ACQF constraints or vocabulary membership changed')
        self.assertEqual(161, len(Collection(new, new.value(NQF, SH['in']))))
        self.assertEqual('https://data.acqf-qcp.africa/model/', str(new.value(NQF, QCP.startsWith)))

    def test_export_is_current_and_reproducible(self):
        content = publisher.render(publisher.build(ROOT, ROOT))
        self.assertEqual(content, publisher.render(publisher.build(ROOT, ROOT)))
        self.assertEqual(content, (ROOT / 'docs/model/exchange-model.ttl').read_text())
        graph = Graph().parse(data=content, format='turtle')
        self.assertFalse(list(graph.triples((None, QCP.controlledVocabulary, None))))
        self.assertFalse(list(graph.triples((None, QCP.controlledVocabularyCatalogue, None))))

    def test_missing_or_empty_catalogue_fails(self):
        for content in ['', '''@prefix skos: <http://www.w3.org/2004/02/skos/core#> .
<https://data.acqf-qcp.africa/model/internal/virtualspace-nqfs> a skos:ConceptScheme .''']:
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                (root / 'publication').mkdir()
                (root / 'profile.ttl').write_text((ROOT / 'docs/model/acqf-profile.ttl').read_text())
                (root / 'empty.ttl').write_text(content)
                (root / 'publication/validation.json').write_text('''{
                  "profile": "profile.ttl", "vocabularies": {
                  "https://data.acqf-qcp.africa/model/internal/virtualspace-nqfs": "empty.ttl"}}''')
                with self.assertRaises(ValueError):
                    publisher.build(root, ROOT)


if __name__ == '__main__':
    unittest.main()
