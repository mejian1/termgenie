package org.bbop.termgenie.core.io;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.tools.ant.filters.StringInputStream;
import org.bbop.termgenie.core.Ontology;
import org.bbop.termgenie.core.TemplateField;
import org.bbop.termgenie.core.TermTemplate;
import org.bbop.termgenie.core.ioc.IOCModule;
import org.bbop.termgenie.core.ioc.TermGenieGuice;
import org.bbop.termgenie.ontology.OntologyConfiguration;
import org.bbop.termgenie.ontology.impl.DefaultOntologyModule;
import org.bbop.termgenie.tools.ResourceLoader;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.Injector;

public class XMLTermTemplateIOTest extends ResourceLoader {

	public XMLTermTemplateIOTest() {
		super(false);
	}

	private static FlatFileTermTemplateIO flatfile;
	private static XMLTermTemplateIO instance;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Injector injector = TermGenieGuice.createInjector(new DefaultOntologyModule(),
				new IOCModule() {

					@Override
					protected void configure() {
						bind(TemplateOntologyHelper.class).to(TemplateOntologyHelperImpl.class);
					}
				});
		TemplateOntologyHelper helper = injector.getInstance(TemplateOntologyHelper.class);
		flatfile = new FlatFileTermTemplateIO(helper);
		instance = new XMLTermTemplateIO(injector.getInstance(OntologyConfiguration.class));
	}

	@Test
	public void testTemplateIO() throws IOException {
		List<TermTemplate> templates0 = flatfile.readTemplates(loadResource("default_termgenie_rules.txt"));

		String xmlString1 = write(templates0);
		List<TermTemplate> templates1 = read(xmlString1);
		assertTemplateList(templates0, templates1);

		String xmlString2 = write(templates1);
		List<TermTemplate> templates2 = read(xmlString1);
		assertTemplateList(templates0, templates2);

		assertEquals(xmlString1, xmlString2);
	}

	private void assertTemplateList(List<TermTemplate> templates1, List<TermTemplate> templates2) {
		assertEquals(templates1.size(), templates2.size());
		for (int i = 0; i < templates1.size(); i++) {
			TermTemplate t1 = templates1.get(i);
			TermTemplate t2 = templates2.get(i);
			assertNotNull(t1);
			assertNotNull(t2);
			assertEquals(t1.getName(), t2.getName());
			assertEquals(t1.getDisplayName(), t2.getDisplayName());
			assertEquals(t1.getDescription(), t2.getDescription());
			assertEquals(t1.getHint(), t2.getHint());
			assertEquals(t1.getOboNamespace(), t2.getOboNamespace());
			assertEquals(t1.getRules(), t2.getRules());
			assertOntology(t1.getCorrespondingOntology(), t2.getCorrespondingOntology());
			assertOntologies(t1.getExternal(), t2.getExternal());
			assertList(t1.getRequires(), t2.getRequires());
			assertFields(t1.getFields(), t2.getFields());
		}
	}

	private void assertFields(List<TemplateField> fields1, List<TemplateField> fields2) {
		assertNotNull(fields1);
		assertNotNull(fields2);
		assertFalse(fields1.isEmpty());
		assertFalse(fields2.isEmpty());
		for (int i = 0; i < fields1.size(); i++) {
			assertField(fields1.get(i), fields2.get(i));
		}
	}

	private void assertField(TemplateField field1, TemplateField field2) {
		assertEquals(field1.getName(), field2.getName());
		assertEquals(field1.isRequired(), field2.isRequired());
		assertEquals(field1.getCardinality(), field2.getCardinality());
		assertOntologies(field1.getCorrespondingOntologies(), field2.getCorrespondingOntologies());
		assertList(field1.getFunctionalPrefixes(), field2.getFunctionalPrefixes());
	}

	private void assertList(List<String> l1, List<String> l2) {
		if (l1 != l2) {
			assertNotNull(l1);
			assertNotNull(l2);
			assertArrayEquals(l1.toArray(), l2.toArray());
		}
	}

	private void assertOntologies(List<Ontology> l1, List<Ontology> l2) {
		if (l1 != l2) {
			assertNotNull(l1);
			assertNotNull(l2);
			assertEquals(l1.size(), l2.size());
			for (int i = 0; i < l1.size(); i++) {
				assertOntology(l1.get(i), l2.get(i));
			}
		}
	}

	private void assertOntology(Ontology o1, Ontology o2) {
		if (o1 != o2) {
			assertEquals(o1.getUniqueName(), o2.getUniqueName());
			assertEquals(o1.getBranch(), o2.getBranch());
		}
	}

	private String write(Collection<TermTemplate> templates) throws IOException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		instance.writeTemplates(templates, outputStream);
		outputStream.close();
		return outputStream.toString();
	}

	private List<TermTemplate> read(String xmlString) throws IOException {
		StringInputStream inputStream = new StringInputStream(xmlString);
		List<TermTemplate> templates = instance.readTemplates(inputStream);
		inputStream.close();
		return templates;
	}

}