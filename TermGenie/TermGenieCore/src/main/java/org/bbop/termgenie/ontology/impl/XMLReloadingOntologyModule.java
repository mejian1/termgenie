package org.bbop.termgenie.ontology.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.bbop.termgenie.core.ioc.GlobalConfigModule;
import org.bbop.termgenie.tools.Pair;
import org.bbop.termgenie.tools.ResourceLoader;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;

/**
 * Ontology Module, which periodically reloads ontologies from the source and
 * is configured using an XML files.
 */
public class XMLReloadingOntologyModule extends ReloadingOntologyModule {

	private static final String xmlOntologyConfigurationResourceName = "XMLOntologyConfigurationResource";
	
	/**
	 * @param configFile
	 * @param ignoreMappings
	 * @param applicationProperties 
	 */
	public XMLReloadingOntologyModule(String configFile, List<String> ignoreMappings, Properties applicationProperties) {
		super(configFile, ignoreMappings, applicationProperties);
	}

	public XMLReloadingOntologyModule(List<String> ignoreMappings, Properties applicationProperties) {
		this(XMLOntologyConfiguration.SETTINGS_FILE, ignoreMappings, applicationProperties);
	}


	@Override
	public List<Pair<String, String>> getAdditionalData(Injector injector) {
		Key<String> configKey = Key.get(String.class, Names.named(xmlOntologyConfigurationResourceName));
		String configFile = injector.getInstance(configKey);
		Key<Boolean> loadAsFileKey = Key.get(Boolean.class, Names.named(GlobalConfigModule.TryResourceLoadAsFilesName));
		XMLConfigReader reader = new XMLConfigReader(injector.getInstance(loadAsFileKey));
		String config = reader.loadXmlConfig(configFile);
		if (config != null) {
			return Collections.singletonList(new Pair<String, String>(configFile, config));
		}
		return null;
	}
	
	private static class XMLConfigReader extends ResourceLoader {
		
		private static final Logger logger = Logger.getLogger(XMLConfigReader.class);

		XMLConfigReader(boolean tryLoadAsFiles) {
			super(tryLoadAsFiles);
		}
		
		String loadXmlConfig(String resource) {
			InputStream stream = null;
			try {
				stream = loadResourceSimple(resource);
				if (stream != null) {
					return IOUtils.toString(stream);
				}
				logger.warn("Missing resource: "+resource);
			} catch (IOException exception) {
				logger.warn("Could not read from resource: "+resource, exception);
			}
			finally {
				IOUtils.closeQuietly(stream);
			}
			return null;
		}
	}
	
	
}
