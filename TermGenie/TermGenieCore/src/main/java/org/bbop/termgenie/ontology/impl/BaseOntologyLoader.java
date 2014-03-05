package org.bbop.termgenie.ontology.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bbop.termgenie.core.Ontology;
import org.obolibrary.oboformat.parser.OBOFormatParserException;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyIRIMapper;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.UnknownOWLOntologyException;

import owltools.graph.OWLGraphWrapper;
import owltools.io.ParserWrapper;

public class BaseOntologyLoader {

	private ParserWrapper pw;

	protected BaseOntologyLoader(List<OWLOntologyIRIMapper> iriMappers) {
		super();
		pw = new ParserWrapper();
		if (iriMappers != null) {
			for (OWLOntologyIRIMapper iriMapper : iriMappers) {
				pw.addIRIMapper(iriMapper);
			}
		}
	}

	protected synchronized OWLGraphWrapper getResource(Ontology ontology)
			throws OWLOntologyCreationException, IOException, UnknownOWLOntologyException, OBOFormatParserException
	{
		OWLGraphWrapper w = load(ontology.getSource());
		if (w == null) {
			return null;
		}
		final List<String> supports = ontology.getAdditionals();
		if (supports != null) {
			for (String support : supports) {
				OWLOntology owl = loadOwl(support);
				if (owl != null) {
					w.addSupportOntology(owl);
					w.mergeOntology(owl);
				}
			}
		}
		w.addSupportOntologiesFromImportsClosure();
		
		// throws UnknownOWLOntologyException
		w.getAllOntologies();
		
		return w;
	}

	protected synchronized void disposeOntologies() {
		// WARNING: this is also called for invalid states
		// always use a clean new Wrapper
		// there are still issues with cleaning a manager from all previous ontologies
		
		// Step 1: try to clean up existing manager
		List<OWLOntologyIRIMapper> mappers = new ArrayList<OWLOntologyIRIMapper>(pw.getIRIMappers());
		OWLOntologyManager oldManager = pw.getManager();
		Set<OWLOntology> ontologies = oldManager.getOntologies();
		for (OWLOntology ontology : ontologies) {
			oldManager.removeOntology(ontology);
		}
		oldManager.clearIRIMappers();
		
		// Step 2: create a new wrapper and transfer IRI mappers
		ParserWrapper newWrapper = new ParserWrapper();
		newWrapper.addIRIMappers(mappers);
		pw = newWrapper;
	}

	protected OWLGraphWrapper load(String url)
			throws OWLOntologyCreationException, IOException, OBOFormatParserException
	{
		OWLOntology owlOntology = loadOwl(url);
		if (owlOntology == null) {
			return null;
		}
		return new OWLGraphWrapper(owlOntology);
	}
	
	protected OWLOntology loadOwl(String url)
			throws OWLOntologyCreationException, IOException, OBOFormatParserException
	{
		return pw.parse(url);
	}

}
