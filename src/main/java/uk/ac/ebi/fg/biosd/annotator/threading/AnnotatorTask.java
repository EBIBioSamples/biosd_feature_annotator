package uk.ac.ebi.fg.biosd.annotator.threading;

import uk.ac.ebi.utils.threading.BatchServiceTask;

/**
 * The common task used for {@link PropertyValAnnotationService}. It's empty, it's used only to mark the top-level.
 * 
 * @author brandizi
 * <dl><dt>Date:</dt><dd>1 Sep 2015</dd>
 *
 */
public abstract class AnnotatorTask extends BatchServiceTask
{
	public AnnotatorTask ( String name ) {
		super ( name );
	}	
}
