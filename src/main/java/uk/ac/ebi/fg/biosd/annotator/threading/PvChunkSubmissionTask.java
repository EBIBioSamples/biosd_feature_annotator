package uk.ac.ebi.fg.biosd.annotator.threading;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.apache.commons.lang3.RandomUtils;
import org.hibernate.jpa.QueryHints;

import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.resources.Resources;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>1 Sep 2015</dd>
 *
 */
class PvChunkSubmissionTask extends AnnotatorTask
{
	private final int offset, limit;
	private final PropertyValAnnotationService service;
	
	/**
	 * @param name
	 */
	public PvChunkSubmissionTask ( PropertyValAnnotationService service, int offset, int limit )
	{
		super ( "PVCHUNK:" + offset + "-" + ( offset + limit - 1 ) );
		this.offset = offset;
		this.limit = limit;
		this.service = service;
	}

	@Override
	public void run ()
	{
		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
		
		try 
		{
			String hql = "FROM ExperimentalPropertyValue";
			
			Query q = em.createQuery ( hql, ExperimentalPropertyValue.class );
			
			q.setFirstResult ( offset );
			q.setMaxResults ( limit );

			q.setHint ( QueryHints.HINT_READONLY, true );
			
			List<ExperimentalPropertyValue<ExperimentalPropertyType>> pvs = 
				(List<ExperimentalPropertyValue<ExperimentalPropertyType>>) q.getResultList ();
			
			int npvs = pvs.size ();
			
			for ( int i = 0;  i < npvs; i++ )
				if ( this.service.randomSelectionQuota == 1d 
						 || RandomUtils.nextDouble (0d, 1d) <= this.service.randomSelectionQuota )
					this.service.submit ( pvs.get ( i ) );
		}
		finally {
			if ( em.isOpen () ) em.close ();
		}		
	}

}
