package uk.ac.ebi.fg.biosd.annotator.threading;

import javax.persistence.PersistenceException;

import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotationManager;
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
	private final long propertyValueId; 
	private final PropertyValAnnotationManager pvAnnMgr;
	
	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	/**
	 * We share a single instance of the annotator, which keeps links to caches and the like.
	 */
	public PropertyValAnnotationTask ( long pvalId, PropertyValAnnotationManager pvAnnMgr )
	{
		super ( "ANN:" + pvalId );
		this.propertyValueId = pvalId;
		this.pvAnnMgr = pvAnnMgr;
	}

	/**
	 * Runs the annotation task, plus some wrapping about exception handling.
	 */
	@Override
	public void run ()
	{
		try 
		{
			PersistenceException theEx = null;
			
			// Try more times, in the attempt to face concurrency issues we have in cluster mode.
			for ( int attempts = 5; attempts > 0; attempts-- )
			{
				try 
				{
					pvAnnMgr.annotate ( this.propertyValueId );
					return;
				}
				catch ( PersistenceException ex )
				{
					log.trace ( String.format ( 
						"Database error: '%s', probably due to concurrency issues, retrying %d more time(s)", ex.getMessage (), attempts ), 
						ex
					);
					theEx = ex;
					Thread.sleep ( RandomUtils.nextLong ( 50, 1000 ) );
				}
			}
			throw new PersistenceException ( 
				"Couldn't save annotations into the database, giving up after 5 attempts, likely due to: " + theEx.getMessage (),
				theEx 
			);
		}
		catch ( Exception ex ) 
		{
			// TODO: proper exit code
			log.error ( "Error while annotating property value #" + this.propertyValueId + ": " + ex.getMessage (), ex );
			exitCode = 1;
		}
	}

}
