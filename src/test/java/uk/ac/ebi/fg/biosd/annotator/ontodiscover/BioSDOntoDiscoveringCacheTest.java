package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static uk.ac.ebi.fg.biosd.annotator.ontodiscover.BioSDOntoDiscoveringCache.NULL_TERM_URI;

import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.persistence.AnnotatorPersister;
import uk.ac.ebi.fg.biosd.annotator.purge.Purger;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.toplevel.TextAnnotation;
import uk.ac.ebi.fgpt.zooma.search.ZOOMASearchClient;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.CachedOntoTermDiscoverer;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntoTermDiscoveryMemCache;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer.DiscoveredTerm;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.ZoomaOntoTermDiscoverer;
import uk.ac.ebi.utils.time.XStopWatch;

/**
 * Test the {@link BioSDOntoDiscoveringCache}.
 *
 * <dl><dt>date</dt><dd>29 Aug 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
@SuppressWarnings ( "unchecked" )
public class BioSDOntoDiscoveringCacheTest
{
	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	@Before
	public void initResources () {
		AnnotatorResources.reset ();
	}
	
	@After
	public void cleanUp ()
	{
		new Purger ().purge ( new DateTime ().minusMinutes ( 1 ).toDate (), new Date() );
	}
	
	
	@Test
	public void testDbCache()
	{
		// Create the property
		//
		String value = "homo sapiens", type = "specie";

		ExperimentalPropertyType ptype = new ExperimentalPropertyType ( type );
		ExperimentalPropertyValue<ExperimentalPropertyType> pval 
			= new ExperimentalPropertyValue<> ( value, ptype );
		
		EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
		EntityManager em = emf.createEntityManager ();
		
		EntityTransaction tx = em.getTransaction ();
		tx.begin ();
		em.persist ( pval );
		tx.commit ();
		em.close ();
		
		XStopWatch timer = new XStopWatch ();
		
		BioSDOntoDiscoveringCache baseCache = new BioSDOntoDiscoveringCache ();
		ZoomaOntoTermDiscoverer zoomaDiscoverer = new ZoomaOntoTermDiscoverer ( new ZOOMASearchClient () );
		zoomaDiscoverer.setZoomaThreesholdScore ( 54.0f );
		OntologyTermDiscoverer client = new CachedOntoTermDiscoverer ( zoomaDiscoverer, baseCache );
		
		timer.start ();
		
		// Annotate this property
		//
		List<DiscoveredTerm> terms = client.getOntologyTermUris ( value, type );
		long time1 = timer.getTime ();
		
		log.info ( "Discovered entries:\n{}", terms.toString () );
			
		ExtendedDiscoveredTerm dterm = (ExtendedDiscoveredTerm) terms.iterator ().next ();
		String termUri = dterm.getUri ().toASCIIString ();
		float termScore = dterm.getScore ();
		int nterms = terms.size ();
		
		// Now save it to the DB
		for ( DiscoveredTerm dtermi: terms )
			pval.addOntologyTerm ( ( (ExtendedDiscoveredTerm) dtermi).getOntologyTerm () );
		
		// we have to do it this indirect way, due to the way the persister is designed to work
		//
		AnnotatorResources.getInstance ().getStore ().find ( pval, pval.getId ().toString () ); 
		AnnotatorPersister persister = new AnnotatorPersister (); // it fetches the prop back from the store
		persister.persist ();
		
		
		// verify it went to the DB
		//
		TextAnnotation zoomaMarker = BioSDOntoDiscoveringCache.createZOOMAMarker ( value, type );

		em = emf.createEntityManager ();
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
		
		// Lookup again, now search times must be much faster, thanks to the cache
		//
		for ( int i = 0; i < 100; i++ )
		{
			terms = client.getOntologyTermUris ( value, type );
			log.trace ( "Call {}, time {}", i, timer.getTime () );
		}
		timer.stop ();
		
		double time2 = timer.getTime () / 100.0;
		
		log.info ( "Second-call versus first-call time: {}, {}", time2, time1 );
		assertTrue ( "WTH?! Second call time bigger than first!", time2 < time1 );
		
		em.close ();
	}
	
	/**
	 * Tests that string values not related to ontologies are actually associated to the 
	 * {@link BioSDOntoDiscoveringCache#NULL_TERM_URI} and corresponding objects are created in the BioSD db.
	 * 
	 */
	@Test
	public void testNullMapping ()
	{
		BioSDOntoDiscoveringCache baseCache = new BioSDOntoDiscoveringCache ();
		ZoomaOntoTermDiscoverer zoomaDiscoverer = new ZoomaOntoTermDiscoverer ( new ZOOMASearchClient () );
		OntologyTermDiscoverer client = new CachedOntoTermDiscoverer ( zoomaDiscoverer, baseCache );

		// Create and annotate the property
		//
		String value = "bla bla foo value 1234", type = "foo type 2233";

		List<DiscoveredTerm> terms = client.getOntologyTermUris ( value, type );
		
		assertTrue ( "Wrong mapping returned!", terms.isEmpty () );

		
		// Now persist results in memory
		AnnotatorPersister persister = new AnnotatorPersister ();
		persister.persist ();

		
		// Verify there is the OE and the annotation
		//
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

		em.close ();
		
	}
	
	
	/**
	 * Tests that {@link BioSDOntoDiscoveringCache} and {@link OntologyTermDiscoverer} are used correctly when 
	 * combined together.
	 */
	@Test
	public void testBothCacheLevels ()
	{
		// The chain is: caller -> cached ( mem cache ) -> cached ( BioSD cache ) -> ZOOMA
		// 
		OntoTermDiscoveryMemCache memCache = new OntoTermDiscoveryMemCache ();
		BioSDOntoDiscoveringCache dbCache = new BioSDOntoDiscoveringCache ();
		
		ZoomaOntoTermDiscoverer zoomaDiscoverer = new ZoomaOntoTermDiscoverer ( new ZOOMASearchClient () );
		zoomaDiscoverer.setZoomaThreesholdScore ( 50.0f );
		
		OntologyTermDiscoverer level2Client = new CachedOntoTermDiscoverer ( zoomaDiscoverer, dbCache );
		
		OntologyTermDiscoverer client = new CachedOntoTermDiscoverer ( level2Client, memCache );
	
		
		// Test this property
		//
		String value = "homo sapiens", type = "specie";

		List<DiscoveredTerm> terms = client.getOntologyTermUris ( value, type );
		log.info ( "Discovered entries:\n{}", terms.toString () );

		// Save
		//
		AnnotatorPersister persister = new AnnotatorPersister ();
		persister.persist ();
		
		
		// Test both caches are used. More advanced tests of this are in the ZOOMA client module
		//
		assertNotNull ( "Entry not saved in memory cache!", memCache.getOntologyTermUris ( value, type ) );
		assertNotNull ( "Entry not saved in DB cache!", dbCache.getOntologyTermUris ( value, type ) );
	}
	
}
