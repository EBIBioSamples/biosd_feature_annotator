package uk.ac.ebi.fg.biosd.annotator.test;

import org.junit.rules.ExternalResource;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.threading.PropertyValAnnotationService;

/**
 * Can be used to reset {@link AnnotatorResources}, which is necessary every time you start 
 * {@link PropertyValAnnotationService} with a new set of entities.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>10 Apr 2015</dd>
 *
 */
public class AnnotatorResourcesResetRule extends ExternalResource
{
	@Override
	protected void before () throws Throwable {
		AnnotatorResources.reset ();
	}
}
