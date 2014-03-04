package org.bbop.termgenie.rules;

import static org.junit.Assert.*;

import java.util.Set;

import org.bbop.termgenie.core.ioc.TermGenieGuice;
import org.bbop.termgenie.core.process.ProcessState;
import org.bbop.termgenie.core.rules.ReasonerFactory;
import org.bbop.termgenie.core.rules.ReasonerModule;
import org.bbop.termgenie.ontology.OntologyLoader;
import org.bbop.termgenie.ontology.OntologyTaskManager;
import org.bbop.termgenie.ontology.OntologyTaskManager.OntologyTask;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import owltools.graph.OWLGraphWrapper;

import com.google.inject.Injector;

public class ELKReasonerTest {

	private static OntologyLoader loader;
	private static ReasonerFactory reasonerFactory;

	@BeforeClass
	public static void beforeClass() {
		Injector injector = TermGenieGuice.createInjector(
				new OldTestOntologyModule("ontology-configuration_simple.xml"),
				new ReasonerModule(null));

		loader = injector.getInstance(OntologyLoader.class);
		reasonerFactory = injector.getInstance(ReasonerFactory.class);
	}

	@Test
	public void test1() throws Exception {
		OntologyTaskManager ontologyManager = loader.getOntologyManager();
		ontologyManager.runManagedTask(new OntologyTask(){

			@Override
			protected void runCatching(final OWLGraphWrapper wrapper) throws TaskException, Exception {
				OWLReasoner reasoner = reasonerFactory.createReasoner(wrapper, ProcessState.NO);
				assertTrue(reasoner.isConsistent());
				OWLObject x = wrapper.getOWLObjectByIdentifier("GO:0006915");
				try {
					NodeSet<OWLClass> classes = reasoner.getSubClasses((OWLClassExpression) x, false);
					assertFalse(classes.isEmpty());
					Set<OWLClass> set = classes.getFlattened();
					assertTrue(set.size() >= 48);
				} catch (Throwable exception) {
					exception.printStackTrace();
					fail(exception.getMessage());
				} 
			}
		});

	}
}
