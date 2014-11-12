package uk.ac.ebi.fg.biosd.annotator.purge;

import java.util.Date;

import org.joda.time.DateTime;

/**
 * TODO: Comment me!
 *
 * <dl><dt>date</dt><dd>12 Nov 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class Purger
{
	public int purgeOlderThan ( Date endTime )
	{
		return new ZoomaAnnotationsPurger ().purgeOlderThan ( endTime ); 
	}
	
	public int purgeOlderThan ( int daysAgo )
	{
		return purgeOlderThan ( new DateTime ().minusDays ( daysAgo ).toDate () );
	}
	
	public int purge ( Date startTime, Date endTime )
	{
		return new ZoomaAnnotationsPurger ().purge ( startTime, endTime );
	}
	
}
