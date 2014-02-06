// @requires rules/common.js

function catabolism_to() {
	var ont = GeneOntology; // the graph wrapper contains all info, including CHEBI
	var source = getSingleTerm("source", ont);
	var to = getSingleTerm("to", ont);

	var sourcename = termname(source, ont);
	var toname = termname(to, ont);
	var label = sourcename + " catabolic process to " + toname;
	var definition = "The chemical reactions and pathways resulting in the breakdown of "
					+ sourcename + "to "+ toname + ".";

	var synonyms = null;
//		synonyms = termgenie.addSynonym(label, null, null, tname, ' catabolism', 'EXACT');
//		synonyms = termgenie.addSynonym(label, synonyms, null, tname, ' catabolic process', 'EXACT');
//		synonyms = termgenie.addSynonym(label, synonyms, null, tname, ' breakdown', 'EXACT');
//		synonyms = termgenie.addSynonym(label, synonyms, null, tname, ' degradation', 'EXACT');
		
	var mdef = createMDef("GO_0009056 and 'has input' some ?X and 'has output' some ?T");
	mdef.addParameter('X', source, ont);
	mdef.addParameter('T', to, ont);
	var success = createTerm(label, definition, synonyms, mdef);
}