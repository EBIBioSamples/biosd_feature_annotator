package uk.ac.ebi.fg.biosd.annotator.purge;

import java.util.Date;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import org.apache.commons.lang3.RandomUtils;
import org.hibernate.CacheMode;
import org.hibernate.Query;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.core_model.resources.Resources;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>11 Jul 2015</dd>
 *
 */
public class Purger
{
	private double deletionRate = 1.0;
	private EntityManager entityManager;
	
	private Logger log = LoggerFactory.getLogger ( this.getClass () );

	public int purgeOlderThan ( Date endTime )
	{
		return this.purge ( new Date ( 0 ), endTime ); 
	}
	
	public int purgeOlderThan ( int daysAgo )
	{
		return purgeOlderThan ( new DateTime ().minusDays ( daysAgo ).toDate () );
	}
	
	
	public int purge ( Date startTime, Date endTime ) 
	{
		try
		{
			EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
			this.entityManager = emf.createEntityManager ();
			
			return this.purgeDataItems ( startTime, endTime )
				+ this.purgeResolvedOntoTerms ( startTime, endTime )
				+ this.purgePvAnnotations ( startTime, endTime );
		}
		finally
		{
			if ( this.entityManager != null && this.entityManager.isOpen () )
				this.entityManager.close ();
		}
	}
	
	
	private int purgeDataItems ( Date startTime, Date endTime )
	{
		String hql= "FROM DataItem ann WHERE\n"
			+ "timestamp >= :startTime AND timestamp <= :endTime\n"
			+ "ORDER BY timestamp";
		
		Session session = (Session) this.entityManager.getDelegate ();
		
		Query q = session.createQuery ( hql )
			.setParameter ( "startTime", startTime )
			.setParameter ( "endTime", endTime );

		log.info ( "purging numerical data" );
		int result = purgeEntities ( q );
		log.info ( "done, {} records deleted", result );
		return result;
	}

	private int purgeResolvedOntoTerms ( Date startTime, Date endTime )
	{
		// TODO: first remove ResolvedOntoTermAnnotation instances
		// Then search for ComputedOntoTerm not having their URI in ResolvedOntoTermAnnotation
		// Hope it's fast enough...

		String hql= "FROM ResolvedOntoTermAnnotation ann WHERE\n"
			+ "timestamp >= :startTime AND timestamp <= :endTime\n"
			+ "ORDER BY timestamp";
		
		Session session = (Session) this.entityManager.getDelegate ();
		
		Query q = session.createQuery ( hql )
			.setParameter ( "startTime", startTime )
			.setParameter ( "endTime", endTime );

		log.info ( "purging verified ontology term annotations" );
		int result = purgeEntities ( q );
		
		hql = "FROM ComputedOntoTerm WHERE uri NOT IN ( SELECT ontoTermUri FROM ResolvedOntoTermAnnotation )";
		q = session.createQuery ( hql );
		
		log.info ( "purging verified ontology terms" );
		result += purgeEntities ( q, false );
		log.info ( "done, {} records deleted", result );
		return result;

	}	
	
	
	private int purgePvAnnotations ( Date startTime, Date endTime )
	{
		String hql= "FROM ExpPropValAnnotation ann WHERE\n"
			+ "timestamp >= :startTime AND timestamp <= :endTime\n"
			+ "ORDER BY timestamp";
		
		Session session = (Session) this.entityManager.getDelegate ();
		
		Query q = session.createQuery ( hql )
			.setParameter ( "startTime", startTime )
			.setParameter ( "endTime", endTime );

		log.info ( "purging sample property annotations" );
		int result = purgeEntities ( q );
		log.info ( "done, {} records deleted", result );
		return result;
	}

	private int purgeEntities ( Query qry ) {
		return purgeEntities ( qry, true );
	}
	
	private int purgeEntities ( Query qry, boolean doRandomDeletes )
	{
		int result = 0;
				
		EntityTransaction tx = this.entityManager.getTransaction ();
		tx.begin ();
		
		// TODO: needs hibernate.jdbc.batch_size
		qry
			.setReadOnly ( true )
			.setFetchSize ( 1000 )
			.setCacheMode ( CacheMode.IGNORE );

		for ( ScrollableResults annRs = qry.scroll ( ScrollMode.FORWARD_ONLY ); annRs.next (); )
		{
			// Randomly skip a number of them
			if ( doRandomDeletes && RandomUtils.nextDouble ( 0, 1.0 ) >= deletionRate ) continue;
			
			Object entity = annRs.get ( 0 );
			this.entityManager.remove ( entity );
			result++;
			
			// Flush changes from time to time
			if ( ++result % 100 == 0 )
			{
				this.entityManager.flush ();
				this.entityManager.clear ();
			}
			
			if ( result % 1000 == 0 ) log.info ( "{} entities processed", result );
		}
		
		tx.commit ();
		return result;
	}
	
	
	/**
	 * If &lt; 1, only a fraction of the total annotations selected by the criteria in {@link #purge(Date, Date)}
	 * will be deleted. Ranges from 0 to 1.
	 *   
	 */
	public double getDeletionRate ()
	{
		return deletionRate;
	}

	public void setDeletionRate ( double deletionRate )
	{
		this.deletionRate = deletionRate;
	}
}
