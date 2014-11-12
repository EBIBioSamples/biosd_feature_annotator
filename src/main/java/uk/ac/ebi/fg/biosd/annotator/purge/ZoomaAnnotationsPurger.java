package uk.ac.ebi.fg.biosd.annotator.purge;

import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import org.hibernate.CacheMode;
import org.hibernate.Query;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;

import uk.ac.ebi.fg.biosd.annotator.ontodiscover.BioSDOntoDiscoveringCache;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.toplevel.Annotation;
import uk.ac.ebi.fg.core_model.toplevel.TextAnnotation;

/**
 * TODO: Comment me!
 *
 * <dl><dt>date</dt><dd>11 Nov 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class ZoomaAnnotationsPurger
{
	public int purgeOlderThan ( Date endTime )
	{
		return purge ( new Date ( 0 ), endTime );
	}

	public int purge ( Date startTime, Date endTime )
	{
		int result = 0;
		
		EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
		EntityManager em = emf.createEntityManager ();
		
		Annotation tplAnn = BioSDOntoDiscoveringCache.createZOOMAMarker ( "foo", "foo" );
		
		String hqlAnn = "SELECT ann FROM TextAnnotation ann JOIN ann.type AS atype JOIN ann.provenance AS prov\n"
			+ "WHERE atype.name = '" + tplAnn.getType ().getName () + "'" 
			+ "  AND prov.name = '" + tplAnn.getProvenance ().getName () + "'"
			+ "  AND ann.timestamp >= :startTime"
			+ "  AND ann.timestamp <= :endTime";
						
		Session session = (Session) em.getDelegate ();
		
		Query qAnn = session.createQuery ( hqlAnn )
			.setParameter ( "startTime", startTime )
			.setParameter ( "endTime", endTime );
			
		// TODO: needs hibernate.jdbc.batch_size
		qAnn
			.setReadOnly ( true )
			.setFetchSize ( 1000 )
			.setCacheMode ( CacheMode.IGNORE );
		
		EntityTransaction tx = em.getTransaction ();
		tx.begin ();
		
		long annCt = 0;
		
		// Select all annotations that are too old
		for ( ScrollableResults annRs = qAnn.scroll ( ScrollMode.FORWARD_ONLY ); annRs.next (); )
		{
			TextAnnotation ann = (TextAnnotation) annRs.get ( 0 );
			Long annId = ann.getId ();
			
			// Check all the ontology terms that have old annotations-only attached
			javax.persistence.Query qOe = em.createNamedQuery ( "findOe2BePurged" )
				.setParameter ( "annId", annId )
				.setParameter ( "atype", tplAnn.getType ().getName () )
				.setParameter ( "prov", tplAnn.getProvenance ().getName ()  )
				.setParameter ( "startTime", startTime )
				.setParameter ( "endTime", endTime );
			
			for ( OntologyEntry oe: (List<OntologyEntry>) qOe.getResultList () )
			{
				// Now check all the properties linking to this oe
				String sqlDelPvOe = "DELETE FROM exp_prop_val_onto_entry WHERE oe_id = :oeId";
				javax.persistence.Query sDelOe = em.createNativeQuery ( sqlDelPvOe ).setParameter ( "oeId", oe.getId () );
				result += sDelOe.executeUpdate ();
				
				// Great, now we can get rid of the OE
				em.remove ( oe );
				result++;
			}
			
			// And now we can remove the old annotation
			em.remove ( ann );
			result++;
			
			// Flush changes from time to time
			if ( ++annCt % 100 == 0 )
			{
				em.flush ();
				em.clear ();
			}
		}
		
		tx.commit ();
		em.close ();
		
		return result;
		
	}
	
}
