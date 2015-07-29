package uk.ac.ebi.fg.biosd.annotator.threading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotationManager;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.utils.threading.BatchServiceTask;

/**
 * This wraps the invocation of {@link PropertyValAnnotationManager} into a proper {@link PropertyValAnnotationTask task}
 * for the {@link PropertyValAnnotationService annotator service}. Essentially, a task annotates a single
 * {@link ExperimentalPropertyValue} into a single thread. 
 *
 * <dl><dt>date</dt><dd>3 Sep 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class PropertyValAnnotationTask extends BatchServiceTask
{
	private final ExperimentalPropertyValue<ExperimentalPropertyType> propertyValue; 
	
	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	/**
	 * We share a single instance of the annotator, which keeps links to caches and the like.
	 */
	public PropertyValAnnotationTask ( ExperimentalPropertyValue<ExperimentalPropertyType> pv )
	{
		super ( "ANN:" + pv.getId () );
		this.propertyValue = pv;
	}

	/**
	 * Runs the annotation task, plus some wrapping about exception handling.
	 */
	@Override
	public void run ()
	{
		try 
		{
			PropertyValAnnotationManager pvAnnMgr = AnnotatorResources.getInstance ().getPvAnnMgr ();
			pvAnnMgr.annotate ( this.propertyValue );
		}
		catch ( Exception ex ) 
		{
			// TODO: proper exit code
			log.error ( "Error while annotating property value #" + this.propertyValue + ": " + ex.getMessage (), ex );
			exitCode = 1;
		}
	}

}
