package org.bbop.termgenie.services.review;

import org.bbop.termgenie.ontology.OntologyCommitReviewPipelineStages;
import org.bbop.termgenie.ontology.OntologyTaskManager;
import org.bbop.termgenie.services.InternalSessionHandler;
import org.bbop.termgenie.services.permissions.UserPermissions;
import org.bbop.termgenie.services.review.JsonCommitReviewEntry.JsonDiff;
import org.obolibrary.oboformat.model.Clause;
import org.obolibrary.oboformat.model.Frame;
import org.obolibrary.oboformat.parser.OBOFormatConstants.OboFormatTag;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@Singleton
public class GOTermCommitReviewServiceImpl extends TermCommitReviewServiceImpl {

	@Inject
	GOTermCommitReviewServiceImpl(InternalSessionHandler sessionHandler,
			UserPermissions permissions,
			@Named("TermCommitReviewServiceOntology") OntologyTaskManager ontology,
			OntologyCommitReviewPipelineStages stages)
	{
		super(sessionHandler, permissions, ontology, stages);
	}

	/**
	 * GO specific updates of terms after marking them as obsolete
	 * 
	 * @param jsonDiff
	 * @param frame
	 */
	@Override
	protected void handleObsoleteFrame(JsonDiff jsonDiff, Frame frame) {
		super.handleObsoleteFrame(jsonDiff, frame);
		
		// Prefix Definition with "OBSOLETE. "
		Clause defClause = frame.getClause(OboFormatTag.TAG_DEF);
		if (defClause != null) {
			String value = defClause.getValue(String.class);
			if (value != null) {
				if (!value.startsWith("OBSOLETE.")) {
					value = "OBSOLETE. " + value;
				}
			}
			else {
				value = "OBSOLETE.";
			}
			defClause.setValue(value);
		}
		else {
			frame.addClause(new Clause(OboFormatTag.TAG_DEF, "OBSOLETE."));
		}
		
		// add comment about obsolete
		Clause commentClause = frame.getClause(OboFormatTag.TAG_COMMENT);
		String comment = jsonDiff.getObsoleteComment();
		if (commentClause == null && comment != null && !comment.isEmpty()) {
			// only add the comment if there wasn't any before and its non-empty
			commentClause = new Clause(OboFormatTag.TAG_COMMENT, comment);
			frame.addClause(commentClause);
		}
	}

}
