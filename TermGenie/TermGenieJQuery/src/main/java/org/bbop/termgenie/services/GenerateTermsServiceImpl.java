package org.bbop.termgenie.services;

import static org.bbop.termgenie.tools.ErrorMessages.*;
import static org.bbop.termgenie.tools.TermGenerationMessageTool.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bbop.termgenie.core.OntologyAware.Ontology;
import org.bbop.termgenie.core.OntologyAware.OntologyTerm;
import org.bbop.termgenie.core.TemplateField;
import org.bbop.termgenie.core.TemplateField.Cardinality;
import org.bbop.termgenie.core.TermTemplate;
import org.bbop.termgenie.core.rules.TermGenerationEngine;
import org.bbop.termgenie.core.rules.TermGenerationEngine.MultiValueMap;
import org.bbop.termgenie.core.rules.TermGenerationEngine.TermGenerationInput;
import org.bbop.termgenie.core.rules.TermGenerationEngine.TermGenerationOutput;
import org.bbop.termgenie.core.rules.TermGenerationEngine.TermGenerationParameters;
import org.bbop.termgenie.data.JsonGenerationResponse;
import org.bbop.termgenie.data.JsonTermGenerationInput;
import org.bbop.termgenie.data.JsonTermGenerationParameter;
import org.bbop.termgenie.data.JsonTermGenerationParameter.JsonMultiValueMap;
import org.bbop.termgenie.data.JsonTermGenerationParameter.JsonOntologyTerm;
import org.bbop.termgenie.data.JsonTermTemplate;
import org.bbop.termgenie.data.JsonTermTemplate.JsonCardinality;
import org.bbop.termgenie.data.JsonTermTemplate.JsonTemplateField;
import org.bbop.termgenie.data.JsonValidationHint;
import org.bbop.termgenie.tools.FieldValidatorTool;
import org.bbop.termgenie.tools.ImplementationFactory;
import org.bbop.termgenie.tools.OntologyCommitTool;
import org.bbop.termgenie.tools.OntologyTools;
import org.bbop.termgenie.tools.UserCredentialValidatorTools;
import org.semanticweb.owlapi.model.OWLObject;

import owltools.graph.OWLGraphWrapper;

public class GenerateTermsServiceImpl implements GenerateTermsService {

	private static final TemplateCache TEMPLATE_CACHE = TemplateCache.getInstance();
	private static final OntologyTools ontologyTools = ImplementationFactory.getOntologyTools();
	private static final UserCredentialValidatorTools validator = ImplementationFactory.getUserCredentialValidator();
	private static final TermGenerationEngine termGeneration = ImplementationFactory.getTermGenerationEngine();
	private static final OntologyCommitTool committer = ImplementationFactory.getOntologyCommitTool();
	
	@Override
	public JsonTermTemplate[] availableTermTemplates(String ontologyName) {
		// sanity check
		if (ontologyName == null) {
			// silently ignore this
			return new JsonTermTemplate[0];
		}
		Collection<TermTemplate> templates = getTermTemplates(ontologyName);
		if (templates.isEmpty()) {
			// short cut for empty results.
			return new JsonTermTemplate[0];
		}

		// encode the templates for JSON
		List<JsonTermTemplate> jsonTemplates = new ArrayList<JsonTermTemplate>();
		for (TermTemplate template : templates) {
			jsonTemplates.add(JsonTemplateTools.createJsonTermTemplate(template));
		}
		Collections.sort(jsonTemplates, new Comparator<JsonTermTemplate>() {

			@Override
			public int compare(JsonTermTemplate o1, JsonTermTemplate o2) {
				return o1.getName().compareTo(o2.getName());
			}
		});
		return jsonTemplates.toArray(new JsonTermTemplate[jsonTemplates.size()]);
	}

	/*
	 * Do not trust any input here. Do not assume that this is well formed, as the
	 * request could be generated by a different client!
	 */
	@Override
	public JsonGenerationResponse generateTerms(String ontologyName,
			JsonTermGenerationInput[] allParameters, 
			boolean commit, String username, String password) {
		// sanity checks
		if (ontologyName == null || ontologyName.isEmpty()) {
			return new JsonGenerationResponse(NO_ONTOLOGY, null, null);
		}
		if (allParameters == null) {
			return new JsonGenerationResponse(NO_TERM_GENERATION_PARAMETERS, null, null);
		}
		if (commit) {
			if (username == null || username.isEmpty()) {
				return new JsonGenerationResponse(MISSING_USERNAME, null, null);
			}
		}
		// retrieve target ontology
		Ontology ontology = ontologyTools.getOntology(ontologyName);
		if (ontology == null) {
			return new JsonGenerationResponse(NO_ONTOLOGY, null, null);
		}

		// term generation parameter validation
		List<JsonValidationHint> allErrors = new ArrayList<JsonValidationHint>();
		for (JsonTermGenerationInput input : allParameters) {
			if (input == null) {
				return new JsonGenerationResponse(UNEXPECTED_NULL_VALUE, null, null);
			}
			JsonTermTemplate one = input.getTermTemplate();
			JsonTermGenerationParameter parameter = input.getTermGenerationParameter();
			if (one == null || parameter == null) {
				return new JsonGenerationResponse(UNEXPECTED_NULL_VALUE, null, null);
			}
			// retrieve the template from the server, do not trust the submitted one.
			TermTemplate template = getTermTemplate(ontologyName, one.getName());
			if (template == null) {
				return new JsonGenerationResponse("Unknow template specified: " + one.getName(), null, null);
			}
			JsonTermTemplate jsonTermTemplate = JsonTemplateTools.createJsonTermTemplate(template);
			
			List<JsonValidationHint> errors = FieldValidatorTool.validateParameters(jsonTermTemplate,
					parameter);
			if (!errors.isEmpty()) {
				allErrors.addAll(errors);
			}
		}
		// return validation errors
		if (!allErrors.isEmpty()) {
			return new JsonGenerationResponse(null, allErrors, null);
		}
		if (commit) {
			// check user name and password
			// use java instance, do not do additional round trip via servlet.
			boolean valid = validateCredentials(username, password);
			if (!valid) {
				return new JsonGenerationResponse(UNKOWN_USERNAME_PASSWORD, null, null);
			}
		}
		// generate term candidates
		List<TermGenerationInput> generationTasks = createGenerationTasks(ontologyName, allParameters);
		List<TermGenerationOutput> candidates = generateTermsInternal(ontology, generationTasks);

		// validate candidates (or is this done during the generation?)
		if (candidates == null || candidates.isEmpty()) {
			return new JsonGenerationResponse(NO_TERMS_GENERATED, null, null);
		}
		
		List<String> messages = new ArrayList<String>(candidates.size());
		
		// commit if required
		if (commit) {
			boolean success = executeCommit(ontology, candidates);
			throw new RuntimeException("Not implemented");
			// TODO generate result for a commit (success or error)? status?
		}
		else {
			for(TermGenerationOutput candidate : candidates) {
				messages.add(generateTermValidationMessage(candidate));
			}
		}
		JsonGenerationResponse generationResponse = new JsonGenerationResponse(null, null, messages);
		// return response
		return generationResponse;
	}

	private List<TermGenerationInput> createGenerationTasks(String ontologyName, JsonTermGenerationInput[] allParameters) {
		List<TermGenerationInput> result = new ArrayList<TermGenerationInput>();
		for (JsonTermGenerationInput jsonInput : allParameters) {
			JsonTermTemplate jsonTemplate = jsonInput.getTermTemplate();
			TermTemplate template = getTermTemplate(ontologyName, jsonTemplate.getName());
			TermGenerationParameters parameters = JsonTemplateTools.createTermGenerationParameters(jsonInput.getTermGenerationParameter(), jsonTemplate, template);
			TermGenerationInput input = new TermGenerationInput(template, parameters);
			result.add(input);
		}
		return result;
	}

	private Collection<TermTemplate> getTermTemplates(String ontology) {
		Collection<TermTemplate> templates;
		synchronized (TEMPLATE_CACHE) {
			templates = TEMPLATE_CACHE.getTemplates(ontology);
			if (templates == null) {
				templates = requestTemplates(ontology);
				TEMPLATE_CACHE.put(ontology, templates);
			}
		}
		return templates;
	}

	private TermTemplate getTermTemplate(String ontology, String name) {
		TermTemplate template;
		synchronized (TEMPLATE_CACHE) {
			template = TEMPLATE_CACHE.getTemplate(ontology, name);
			if (template == null) {
				Collection<TermTemplate> templates = TEMPLATE_CACHE.getTemplates(ontology);
				if (templates == null) {
					templates = requestTemplates(ontology);
					TEMPLATE_CACHE.put(ontology, templates);
				}
				template = TEMPLATE_CACHE.getTemplate(ontology, name);
			}
		}
		return template;
	}

	/**
	 * Request the templates for a given ontology.
	 * 
	 * @param ontology
	 * @return templates, never null
	 */
	protected Collection<TermTemplate> requestTemplates(String ontology) {
		List<TermTemplate> templates = ontologyTools.getTermTemplates(ontology);
		if (templates == null) {
			templates = Collections.emptyList();
		}
		return templates;
	}

	protected boolean validateCredentials(String username, String password) {
		return validator.validate(username, password);
	}

	protected List<TermGenerationOutput> generateTermsInternal(Ontology ontology, List<TermGenerationInput> generationTasks) {
		return termGeneration.generateTerms(ontology, generationTasks); 
	}

	protected boolean executeCommit(Ontology ontology, List<TermGenerationOutput> candidates) {
		return committer.commitCandidates(ontology, candidates);
	}

	/**
	 * Tools for converting a term generation details into the JSON specific
	 * (transfer) formats.
	 */
	static class JsonTemplateTools {

		/**
		 * Convert a single template into a JSON specific data structure.
		 * 
		 * @param template
		 * @return internal format
		 */
		static JsonTermTemplate createJsonTermTemplate(TermTemplate template) {
			JsonTermTemplate jsonTermTemplate = new JsonTermTemplate();
			jsonTermTemplate.setName(template.getName());
			List<TemplateField> fields = template.getFields();
			int size = fields.size();
			JsonTemplateField[] jsonFields = new JsonTemplateField[size];
			for (int i = 0; i < size; i++) {
				jsonFields[i] = createJsonTemplateField(fields.get(i));
			}
			jsonTermTemplate.setFields(jsonFields);
			return jsonTermTemplate;
		}

		private static JsonTemplateField createJsonTemplateField(TemplateField field) {
			JsonTemplateField jsonField = new JsonTemplateField();
			jsonField.setName(field.getName());
			jsonField.setRequired(field.isRequired());
			Cardinality c = field.getCardinality();
			jsonField.setCardinality(new JsonCardinality(c.getMinimum(), c.getMaximum()));
			jsonField.setFunctionalPrefixes(field.getFunctionalPrefixes().toArray(new String[0]));
			if (field.hasCorrespondingOntologies()) {
				List<Ontology> ontologies = field.getCorrespondingOntologies();
				String[] ontologyNames = new String[ontologies.size()];
				for (int i = 0; i < ontologyNames.length; i++) {
					Ontology ontology = ontologies.get(i);
					ontologyNames[i] = ontologyTools.getOntologyName(ontology);
				}
				jsonField.setOntologies(ontologyNames);
			}
			return jsonField;
		}
		
		static TermGenerationParameters createTermGenerationParameters(JsonTermGenerationParameter json, JsonTermTemplate jsonTemplate, TermTemplate template) {
			TermGenerationParameters result = new TermGenerationParameters();
			JsonTemplateField[] jsonFields = jsonTemplate.getFields();
			for (JsonTemplateField jsonField : jsonFields) {
				TemplateField field = template.getField(jsonField.getName());
				copyAll(json, result, jsonField, field);
			}
			return result;
		}
		
		private static void copyAll(JsonTermGenerationParameter json, TermGenerationParameters target, JsonTemplateField jsonKey, TemplateField key) {
			copy(json.getPrefixes(), target.getPrefixes(), jsonKey, key);
			copy(json.getStrings(), target.getStrings(), jsonKey, key);
			copyConvert(json.getTerms(), target.getTerms(), jsonKey, key);
		}
		
		private static void copyConvert(JsonMultiValueMap<JsonOntologyTerm> json, MultiValueMap<OntologyTerm> target, JsonTemplateField jsonKey, TemplateField key) {
			int count = json.getCount(jsonKey);
			if (count > 0) {
				for (int i = 0; i < count; i++) {
					JsonOntologyTerm jsonOntologyTerm = json.getValue(jsonKey, i);
					OntologyTerm value = getOntologyTerm(jsonOntologyTerm);
					target.addValue(value, key, i);
				}
			}
		}

		private static OntologyTerm getOntologyTerm(JsonOntologyTerm jsonOntologyTerm) {
			String ontologyName = jsonOntologyTerm.getOntology();
			Ontology ontology = ontologyTools.getOntology(ontologyName);
			
			String id = jsonOntologyTerm.getTermId();
			String label = null;
			String definition = null;
			Set<String> synonyms = null;
			String cdef = null;
			List<String> defxref = null;
			String comment = null;
			
			OWLGraphWrapper realInstance = ontology.getRealInstance();
			if (realInstance != null) {
				OWLObject owlObject = realInstance.getOWLObjectByIdentifier(id);
				if (owlObject != null) {
					label = realInstance.getLabel(owlObject);
					definition = realInstance.getDef(owlObject);
//					synonyms = realInstance.getSynonymStrings(owlObject);
					// TODO replace this with a proper implementation
					defxref = realInstance.getDefXref(owlObject);
					comment = realInstance.getComment(owlObject);
				}
			}
			
			return new OntologyTerm.DefaultOntologyTerm(id, label, definition, synonyms, cdef, defxref, comment); 
		}

		private static <T> void copy(JsonMultiValueMap<T> json, MultiValueMap<T> target, JsonTemplateField jsonKey, TemplateField key) {
			int count = json.getCount(jsonKey);
			if (count > 0) {
				for (int i = 0; i < count; i++) {
					target.addValue(json.getValue(jsonKey, i), key, i);
				}
			}
		}
	}

	static class TemplateCache {
		private static volatile TemplateCache instance = null;
		private final Map<String, Map<String, TermTemplate>> templates;

		private TemplateCache() {
			templates = new HashMap<String, Map<String, TermTemplate>>();
		}

		public synchronized static TemplateCache getInstance() {
			if (instance == null) {
				instance = new TemplateCache();
			}
			return instance;
		}

		void put(String ontology, Collection<TermTemplate> templates) {
			Map<String, TermTemplate> namedValues = new HashMap<String, TermTemplate>();
			for (TermTemplate template : templates) {
				namedValues.put(template.getName(), template);
			}
			if (namedValues.isEmpty()) {
				namedValues = Collections.emptyMap();
			}
			this.templates.put(ontology, namedValues);
		}

		boolean hasOntology(String ontology) {
			return templates.containsKey(ontology);
		}

		Collection<TermTemplate> getTemplates(String ontology) {
			Map<String, TermTemplate> namedValues = templates.get(ontology);
			if (namedValues == null) {
				return null;
			}
			return namedValues.values();
		}

		TermTemplate getTemplate(String ontology, String templateName) {
			Map<String, TermTemplate> namedValues = templates.get(ontology);
			if (namedValues == null) {
				return null;
			}
			return namedValues.get(templateName);
		}
	}
}
