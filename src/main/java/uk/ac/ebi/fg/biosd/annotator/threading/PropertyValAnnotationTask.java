package uk.ac.ebi.fg.biosd.annotator.threading;

import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotator;
import uk.ac.ebi.utils.threading.BatchServiceTask;

/**
 * TODO: Comment me!
 *
 * <dl><dt>date</dt><dd>3 Sep 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class PropertyValAnnotationTask extends BatchServiceTask
{
	private long propertyValueId; 
	private static PropertyValAnnotator annotator = new PropertyValAnnotator ();
	
	/**
	 * @param name
	 */
	public PropertyValAnnotationTask ( long pvalId )
	{
		super ( "ANN:" + pvalId );
		this.propertyValueId = pvalId;
	}

	@Override
	public void run ()
	{
		try {
			annotator.annotate ( this.propertyValueId );
		}
		catch ( Exception ex ) 
		{
			// TODO: proper exit code
			log.error ( "Error while annotating property value #" + this.propertyValueId + ": " + ex.getMessage (), ex );
			exitCode = 1;
		}
	}

}
