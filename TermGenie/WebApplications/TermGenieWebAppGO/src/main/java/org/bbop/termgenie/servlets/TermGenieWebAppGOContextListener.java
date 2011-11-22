package org.bbop.termgenie.servlets;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.bbop.termgenie.core.ioc.IOCModule;
import org.bbop.termgenie.ontology.AdvancedPersistenceModule;
import org.bbop.termgenie.ontology.OntologyConfiguration;
import org.bbop.termgenie.ontology.OntologyLoader;
import org.bbop.termgenie.ontology.OntologyTaskManager;
import org.bbop.termgenie.ontology.go.cvs.GoCommitReviewCvsModule;
import org.bbop.termgenie.ontology.impl.CommitAwareOntologyLoader;
import org.bbop.termgenie.ontology.impl.ConfiguredOntology;
import org.bbop.termgenie.ontology.impl.XMLReloadingOntologyModule;
import org.bbop.termgenie.presistence.PersistenceBasicModule;
import org.bbop.termgenie.rules.XMLDynamicRulesModule;
import org.bbop.termgenie.services.GoTermCommitServiceImpl;
import org.bbop.termgenie.services.TermCommitService;
import org.bbop.termgenie.services.TermGenieServiceModule;
import org.bbop.termgenie.services.permissions.UserPermissionsModule;
import org.bbop.termgenie.services.review.TermCommitReviewServiceModule;

import com.google.inject.Singleton;

public class TermGenieWebAppGOContextListener extends AbstractTermGenieContextListener {

	private static final Logger logger = Logger.getLogger(TermGenieWebAppGOContextListener.class);
	
	public TermGenieWebAppGOContextListener() {
		super("TermGenieWebAppGOConfigFile");
	}
	
	@Override
	protected IOCModule getUserPermissionModule() {
		return new UserPermissionsModule("termgenie-go", applicationProperties);
	}
	
	@Override
	protected TermGenieServiceModule getServiceModule() {
		return new TermGenieServiceModule(applicationProperties, "TermGenieServiceModule") {

			@Override
			protected void bindTermCommitService() {
				bind(TermCommitService.class, GoTermCommitServiceImpl.class);
			}
		};
	}

	@Override
	protected IOCModule getOntologyModule() {
		return new XMLReloadingOntologyModule("ontology-configuration_go.xml", applicationProperties) {

			@Override
			protected void bindOntologyLoader() {
				bind(OntologyLoader.class, CommitAwareOntologyLoader.class);
				bind("ReloadingOntologyLoaderPeriod", new Long(6L));
				bind("ReloadingOntologyLoaderTimeUnit", TimeUnit.HOURS);
			}
		};
	}

	@Override
	protected IOCModule getRulesModule() {
		return new XMLDynamicRulesModule("termgenie_rules_go.xml", applicationProperties);
	}

	@Override
	protected IOCModule getCommitModule() {
		String cvsFileName = "go/ontology/editors/gene_ontology_write.obo";
		String cvsRoot = ":pserver:anonymous@cvs.geneontology.org:/anoncvs";
		return new GoCommitReviewCvsModule(cvsFileName, cvsRoot, applicationProperties);
	}
	
	

	@Override
	protected TermCommitReviewServiceModule getCommitReviewWebModule() {
		return new TermCommitReviewServiceModule(true, applicationProperties, "CommitReviewWebModule") {

			@Override
			@Singleton
			protected OntologyTaskManager getTermCommitReviewServiceOntology(OntologyConfiguration configuration,  OntologyLoader ontologyLoader)
			{
				ConfiguredOntology configuredOntology = configuration.getOntologyConfigurations().get("GeneOntology");
				return ontologyLoader.getOntology(configuredOntology);
			}
			
		};
	}

	@Override
	protected Collection<IOCModule> getAdditionalModules() {
		List<IOCModule> modules = new ArrayList<IOCModule>();
		try {
			// basic persistence
			String dbFolderString = IOCModule.getSystemProperty("TermgenieWebappGODatabaseFolder", applicationProperties);
			File dbFolder;
			if (dbFolderString != null && !dbFolderString.isEmpty()) {
				dbFolder = new File(dbFolderString);
			}
			else {
				dbFolder = new File(FileUtils.getUserDirectory(), "termgenie-go-db");
			}
			logger.info("Using db folder: "+dbFolder);
			FileUtils.forceMkdir(dbFolder);
			modules.add(new PersistenceBasicModule(dbFolder, applicationProperties));
		} catch (IOException exception) {
			throw new RuntimeException(exception);
		}
		// commit history and ontology id store
		modules.add(new AdvancedPersistenceModule("GO-ID-Manager", "go-id-manager.conf", applicationProperties));
		return modules;
	}

}
