package uk.ac.ebi.fg.biosd.annotator.persistence;

import org.hibernate.event.internal.EntityCopyNotAllowedObserver;
import org.hibernate.event.spi.EventSource;

import uk.ac.ebi.fg.core_model.toplevel.AnnotationProvenance;

/**
 * I'm experiencing <a href = 'https://hibernate.atlassian.net/browse/HHH-9106'>this problem</a> with certain classes,
 * for which I'm sure I have identical copies. So this observer allows to merge copies of such classes and keep 
 * causing an exceptions for others.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>16 Apr 2015</dd>
 *
 */
public class EntityCopySomeAllowedObserver extends EntityCopyNotAllowedObserver
{

	@Override
	public void entityCopyDetected ( Object managedEntity, Object mergeEntity1, Object mergeEntity2, EventSource session )
	{
		if ( managedEntity instanceof AnnotationProvenance ) return;
		super.entityCopyDetected ( managedEntity, mergeEntity1, mergeEntity2, session );
	}
	
}
