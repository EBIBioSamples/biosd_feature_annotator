package uk.ac.ebi.fg.biosd.annotator.threading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.utils.threading.BatchServiceTask;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>1 Sep 2015</dd>
 *
 */
public abstract class AnnotatorTask extends BatchServiceTask
{
	protected Logger log = LoggerFactory.getLogger ( this.getClass () );

	public AnnotatorTask ( String name ) {
		super ( name );
	}	
}
