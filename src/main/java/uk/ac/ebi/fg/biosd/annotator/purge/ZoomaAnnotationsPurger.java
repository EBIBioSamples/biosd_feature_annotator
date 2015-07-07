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
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.OntoDiscoveryAndAnnotator;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.toplevel.Annotation;
import uk.ac.ebi.fg.core_model.toplevel.TextAnnotation;

/**
 * Removes old annotations created by this tool and related to ZOOMA.
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
		return purgeOeAnns ( startTime, endTime ) + purgePvAnns ( startTime, endTime );
	}


	/**
	 * Removes ontology terms created via ZOOMA and their {@link TextAnnotation}.
	 */
	@SuppressWarnings ( "unchecked" )
	private int purgeOeAnns ( Date startTime, Date endTime )
	{
		int result = 0;
		
		EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
		EntityManager em = emf.createEntityManager ();
		
		Annotation tplAnn = null; // BioSDOntoDiscoveringCache.createZOOMAMarker ( "foo", "foo" );
		
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
				String sqlDelPvOe = "DELETE FROM exp_prop_val_onto_entry WHERE oe_id = :oeId";
				result += em.createNativeQuery ( sqlDelPvOe ).setParameter ( "oeId", oe.getId () ).executeUpdate ();

				// And all the units
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
	 * Removes {@link OntoDiscoveryAndAnnotator#createEmptyZoomaMappingMarker() 'no ontology' markers} from property
	 * values. This are created by the {@link OntoDiscoveryAndAnnotator ZOOMA-based ontology discoverer}, when it 
	 * finds that a property value isn't associated to ano ontology term. Removing these annotations cause the PV
	 * to be reconsidered in future, when the annotator is run again. The {@link #getDeletionRate()} affects this
	 * method. 
	 */
	private int purgePvAnns ( Date startTime, Date endTime )
	{
		int result = 0;
		
		EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
		EntityManager em = emf.createEntityManager ();

		TextAnnotation emptyZoomaMapMarker = null; // = OntoDiscoveryAndAnnotator.createEmptyZoomaMappingMarker ();
		
		String hqlAnn = "SELECT ann FROM Annotation ann WHERE\n"
			+ "  ann.type.name = :type"
			+ "  AND ann.timestamp >= :startTime"
			+ "  AND ann.timestamp <= :endTime";
		
		Session session = (Session) em.getDelegate ();
		Query qAnn = session.createQuery ( hqlAnn );
		
		qAnn
			.setParameter ( "type", emptyZoomaMapMarker.getType ().getName () )
			.setParameter ( "startTime", startTime )
			.setParameter ( "endTime", endTime )
			.setReadOnly ( true )
			.setFetchSize ( 1000 )
			.setCacheMode ( CacheMode.IGNORE );
		
		int annCt = 0;
		EntityTransaction tx = em.getTransaction ();
		tx.begin ();

		for ( ScrollableResults annRs = qAnn.scroll ( ScrollMode.FORWARD_ONLY ); annRs.next (); )
		{
			if ( RandomUtils.nextDouble ( 0, 1.0 ) >= deletionRate ) continue;

			TextAnnotation ann = (TextAnnotation) annRs.get ( 0 );
			result += em.createNativeQuery ( 
				"DELETE FROM exp_prop_val_annotation WHERE annotation_id = " + ann.getId () ).executeUpdate ();
			em.remove ( ann );
			result++;
			
			// Flush changes from time to time
			if ( ++annCt % 100 == 0 )
			{
				em.flush ();
				em.clear ();
			}
			
			if ( annCt % 1000 == 0 ) log.info ( "{} property value annotations processed", annCt );
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
