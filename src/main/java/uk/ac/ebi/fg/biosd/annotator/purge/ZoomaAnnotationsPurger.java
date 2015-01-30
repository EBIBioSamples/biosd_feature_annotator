package uk.ac.ebi.fg.biosd.annotator.purge;

import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import org.apache.commons.lang3.RandomUtils;
import org.hibernate.CacheMode;
import org.hibernate.Query;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.ontodiscover.BioSDOntoDiscoveringCache;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.toplevel.Annotation;
import uk.ac.ebi.fg.core_model.toplevel.TextAnnotation;

/**
 * Removes old {@link OntologyEntry}es that ZOOMA has attached to {@link ExperimentalPropertyValue}s, including 
 * the {@link TextAnnotation}s that track their ZOOMA provenance.  
 *
 * <dl><dt>date</dt><dd>11 Nov 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class ZoomaAnnotationsPurger
{
	private double deletionRate = 1.0;
	
	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	public int purgeOlderThan ( Date endTime )
	{
		return purge ( new Date ( 0 ), endTime );
	}
	
	/**
	 * @see #getDeletionRate();
	 */
	public int purge ( Date startTime, Date endTime )
	{
		int result = 0;
		
		EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
		EntityManager em = emf.createEntityManager ();
		
		Annotation tplAnn = BioSDOntoDiscoveringCache.createZOOMAMarker ( "foo", "foo" );
		
		// Find all relevant annotations
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
		
		// Start from the old annotations
		for ( ScrollableResults annRs = qAnn.scroll ( ScrollMode.FORWARD_ONLY ); annRs.next (); )
		{
			// Randomly skip a number of them
			if ( RandomUtils.nextDouble ( 0, 1.0 ) >= deletionRate ) continue;
				
			TextAnnotation ann = (TextAnnotation) annRs.get ( 0 );
			Long annId = ann.getId ();
			
			// Check all the ontology terms that have old annotations-only attached
			// These were created automatically and needs to be removed
			//
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
				result += em.createNativeQuery ( sqlDelPvOe ).setParameter ( "oeId", oe.getId () ).executeUpdate ();

				String sqlDelUnitOe = "DELETE FROM unit_onto_entry WHERE oe_id = :oeId";
				javax.persistence.Query sDelOe = em.createNativeQuery ( sqlDelUnitOe ).setParameter ( "oeId", oe.getId () );
				result += sDelOe.executeUpdate ();
				
				// Great, now we can get rid of the OE
				em.remove ( oe );
				result++;
			}
			
			// The current old annotation is going to be removed too, but first the links with OEs which were kept on 
			// by other annotations
			String sqlDelOeAnn = "DELETE FROM onto_entry_annotation WHERE annotation_id = :annId";
			javax.persistence.Query sDelOe = em.createNativeQuery ( sqlDelOeAnn ).setParameter ( "annId", ann.getId () );
			result += sDelOe.executeUpdate ();
			
			// And now we can go with the annotation
			em.remove ( ann );
			result++;
			
			// Flush changes from time to time
			if ( ++annCt % 100 == 0 )
			{
				em.flush ();
				em.clear ();
			}
			
			if ( annCt % 1000 == 0 ) log.info ( "{} annotations processed", annCt );
		}
		
		tx.commit ();
		em.close ();
		
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
