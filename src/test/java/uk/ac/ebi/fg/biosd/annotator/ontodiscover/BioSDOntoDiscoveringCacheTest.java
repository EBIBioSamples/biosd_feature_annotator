package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static uk.ac.ebi.fg.biosd.annotator.ontodiscover.BioSDOntoDiscoveringCache.NULL_TERM_URI;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.ontodiscover.BioSDOntoDiscoveringCache;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.toplevel.Annotation;
import uk.ac.ebi.fg.core_model.toplevel.TextAnnotation;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.CachedOntoTermDiscoverer;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntoTermDiscoveryMemCache;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer.DiscoveredTerm;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.ZoomaOntoTermDiscoverer;
import uk.ac.ebi.utils.time.XStopWatch;

/**
 * TODO: Comment me!
 *
 * <dl><dt>date</dt><dd>29 Aug 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
@SuppressWarnings ( "unchecked" )
public class BioSDOntoDiscoveringCacheTest
{
	private Logger log = LoggerFactory.getLogger ( this.getClass () );

	@Test
	public void testDbCache()
	{
		XStopWatch timer = new XStopWatch ();
		
		BioSDOntoDiscoveringCache baseCache = new BioSDOntoDiscoveringCache ();
		ZoomaOntoTermDiscoverer zoomaDiscoverer = new ZoomaOntoTermDiscoverer ();
		zoomaDiscoverer.setZoomaThreesholdScore ( 54.0f );
		OntologyTermDiscoverer client = new CachedOntoTermDiscoverer ( zoomaDiscoverer, baseCache );

		
		timer.start ();
		
		String value = "homo sapiens", type = "specie";
		
		List<DiscoveredTerm> terms = client.getOntologyTermUris ( value, type );
		long time1 = timer.getTime ();
		
		log.info ( "Discovered entries:\n{}", terms.toString () );
			
		String termUri = terms.iterator ().next ().getUri ().toASCIIString ();
		float termScore = terms.iterator ().next ().getScore ();
		int nterms = terms.size ();
		
		// verify it went to the DB
		TextAnnotation zoomaMarker = BioSDOntoDiscoveringCache.createZOOMAMarker ( value, type );

		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
		
		List<Object[]> dbentries = em.createNamedQuery ( "findOntoAnnotations" )
	  .setParameter ( "provenance", zoomaMarker.getProvenance ().getName () )
	  .setParameter ( "annotation", zoomaMarker.getText () )
		.getResultList ();
		
		assertEquals ( "entries not saved in the cache!", nterms, dbentries.size () );
		
		boolean hasEntry = false;
		for ( Object[] tuple: dbentries )
		{
			OntologyEntry dbOe = (OntologyEntry) tuple [ 0 ];
			double dbScore =  ((Number) tuple [ 1 ]).doubleValue ();
			log.info ( "Tuple in the DB: {}, {}", dbOe, dbScore );
			
			if ( termUri.equals ( dbOe.getAcc () ) && termScore == dbScore )
				hasEntry = true;
		}
		
		assertTrue ( "Original Entry not found in the DB cache!", hasEntry );
		
		
		timer.reset ();
		timer.start ();
		for ( int i = 0; i < 100; i++ )
		{
			terms = client.getOntologyTermUris ( value, type );
			log.trace ( "Call {}, time {}", i, timer.getTime () );
		}
		timer.stop ();
		
		double time2 = timer.getTime () / 100.0;
		
		log.info ( "Second-call versus first-call time: {}, {}", time2, time1 );
		assertTrue ( "WTH?! Second call time bigger than first!", time2 < time1 );
		
		// Clean-up
		// TODO: use the DAOs 
		EntityTransaction tx = em.getTransaction ();
		tx.begin ();
		for ( Object[] tuple: dbentries )
		{
			OntologyEntry dbOe = (OntologyEntry) tuple [ 0 ];
			for ( Annotation ann: dbOe.getAnnotations () )
				em.remove ( ann );
			em.remove ( dbOe );
		}
		tx.commit ();
	}
	
	
	@Test
	public void testNullMapping ()
	{
		BioSDOntoDiscoveringCache baseCache = new BioSDOntoDiscoveringCache ();
		ZoomaOntoTermDiscoverer zoomaDiscoverer = new ZoomaOntoTermDiscoverer ();
		OntologyTermDiscoverer client = new CachedOntoTermDiscoverer ( zoomaDiscoverer, baseCache );

		String value = "bla bla foo value 1234", type = "foo type 2233";

		List<DiscoveredTerm> terms = client.getOntologyTermUris ( value, type );
		
		assertTrue ( "Wron mapping returned!", terms.isEmpty () );
		

		TextAnnotation zoomaMarker = BioSDOntoDiscoveringCache.createZOOMAMarker ( value, type );

		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
		
		List<Object[]> dbentries = em.createNamedQuery ( "findOntoAnnotations" )
	  .setParameter ( "provenance", zoomaMarker.getProvenance ().getName () )
	  .setParameter ( "annotation", zoomaMarker.getText () )
		.getResultList ();
		
		assertEquals ( "nothing saved in the cache!", 1, dbentries.size () );
				
		OntologyEntry dbOe = ((OntologyEntry) dbentries.get ( 0 ) [ 0 ]);
		
		assertEquals ( "Null mapping didn't save null ontology term!", 
			NULL_TERM_URI, 
			dbOe.getAcc ()
		);
		
		EntityTransaction tx = em.getTransaction ();
		tx.begin ();
		for ( Annotation ann: dbOe.getAnnotations () )
			em.remove ( ann );
		em.remove ( dbOe );
		tx.commit ();
	}
	
	
	@Test
	public void testBothCacheLevels ()
	{
		OntoTermDiscoveryMemCache memCache = new OntoTermDiscoveryMemCache ();
		BioSDOntoDiscoveringCache dbCache = new BioSDOntoDiscoveringCache ();
		
		ZoomaOntoTermDiscoverer zoomaDiscoverer = new ZoomaOntoTermDiscoverer ();
		zoomaDiscoverer.setZoomaThreesholdScore ( 50.0f );
		
		OntologyTermDiscoverer level2Client = new CachedOntoTermDiscoverer ( zoomaDiscoverer, dbCache );
		
		OntologyTermDiscoverer client = new CachedOntoTermDiscoverer ( level2Client, memCache );
	

		String value = "homo sapiens", type = "specie";

		List<DiscoveredTerm> terms = client.getOntologyTermUris ( value, type );
		log.info ( "Discovered entries:\n{}", terms.toString () );

		
		assertNotNull ( "Entry not saved in memory cache!", memCache.getOntologyTermUris ( value, type ) );
		assertNotNull ( "Entry not saved in DB cache!", dbCache.getOntologyTermUris ( value, type ) );
		
		
		
		// Clean-up
		// TODO: use the DAOs 
		//
		
		TextAnnotation zoomaMarker = BioSDOntoDiscoveringCache.createZOOMAMarker ( value, type );

		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
		
		List<Object[]> dbentries = em.createNamedQuery ( "findOntoAnnotations" )
	  .setParameter ( "provenance", zoomaMarker.getProvenance ().getName () )
	  .setParameter ( "annotation", zoomaMarker.getText () )
		.getResultList ();
		
		EntityTransaction tx = em.getTransaction ();
		tx.begin ();
		for ( Object[] tuple: dbentries )
		{
			OntologyEntry dbOe = (OntologyEntry) tuple [ 0 ];
			for ( Annotation ann: dbOe.getAnnotations () )
				em.remove ( ann );
			em.remove ( dbOe );
		}
		tx.commit ();
	}
	
}
