package uk.ac.ebi.fg.biosd.annotator.persistence;

import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.Store;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>19 Mar 2015</dd>
 *
 */
public class SynchronizedStore implements Store
{
	private final Store base;
	
	public SynchronizedStore ( Store base ) {
		this.base = base;
	}


	@Override
	public synchronized <T> T find ( T newObject, String... targetIds )
	{
		return base.find ( newObject, targetIds );
	}


	@SuppressWarnings ( "unchecked" )
	public <S extends Store> S getBase ()
	{
		return (S) base;
	}
	
}
