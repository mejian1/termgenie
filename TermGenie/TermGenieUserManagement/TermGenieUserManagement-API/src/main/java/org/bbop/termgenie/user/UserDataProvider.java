package org.bbop.termgenie.user;

import java.util.List;


public interface UserDataProvider {

	/**
	 * Retrieve the {@link UserData} for the given e-mail
	 * 
	 * @param email
	 * @return user data (never null)
	 */
	public UserData getUserDataPerEMail(String email);
	
	/**
	 * Retrieve the {@link UserData} for the given id, use e-mails as fall-back.
	 * 
	 * @param guid
	 * @param emails
	 * @return user data (never null)
	 */
	public UserData getUserDataPerGuid(String guid, List<String> emails);
	
	/**
	 * Retrieve the list of available Xrefs.
	 * 
	 * @return list of xref information
	 */
	public List<XrefUserData> getXrefUserData();
	
	/**
	 * Retrieve the list of available orcids.
	 * 
	 * @return list of orcid information
	 */
	public List<OrcidUserData> getOrcIdUserData();
}
