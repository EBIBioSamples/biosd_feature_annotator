package uk.ac.ebi.fg.biosd.annotator.threading;

import javax.persistence.PersistenceException;

import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	private final long propertyValueId; 
	private final PropertyValAnnotator pvalAnnotator;
	
	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	/**
	 * @param name
	 */
	public PropertyValAnnotationTask ( long pvalId, PropertyValAnnotator pvalAnnotator )
	{
		super ( "ANN:" + pvalId );
		this.propertyValueId = pvalId;
		this.pvalAnnotator = pvalAnnotator;
	}

	@Override
	public void run ()
	{
		try 
		{
			PersistenceException theEx = null;
			
			for ( int attempts = 5; attempts > 0; attempts-- )
			{
				try 
				{
					pvalAnnotator.annotate ( this.propertyValueId );
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
