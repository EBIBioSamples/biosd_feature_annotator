package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotationManager.ONTO_DISCOVERER_PROP_NAME;

import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotationManager;
import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.fg.biosd.annotator.olsclient.client.OLSClient;
import uk.ac.ebi.fg.biosd.annotator.olsclient.ontodiscovery.OLSOntoTermDiscoverer;
import uk.ac.ebi.fg.biosd.annotator.persistence.AnnotatorPersister;
import uk.ac.ebi.fg.biosd.annotator.purge.Purger;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fgpt.zooma.search.AbstractZOOMASearch;
import uk.ac.ebi.fgpt.zooma.search.ZOOMASearchClient;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.ZoomaOntoTermDiscoverer;
import uk.ac.ebi.onto_discovery.api.CachedOntoTermDiscoverer;
import uk.ac.ebi.onto_discovery.api.OntologyTermDiscoverer;
import uk.ac.ebi.onto_discovery.api.OntologyTermDiscoverer.DiscoveredTerm;
import uk.ac.ebi.utils.time.XStopWatch;

/**
 * Test the {@link BioSDOntoDiscoveringCache}.
 *
 * <dl><dt>date</dt><dd>29 Aug 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class BioSDOntoDiscoveringCacheTest
{
	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	private OntologyTermDiscoverer ontoTermDisvoverer;
	private BioSDOntoDiscoveringCache biosdCache;
	
	
	@Before
	public void initResources () 
	{
		AnnotatorResources.getInstance ().reset ();

		biosdCache = new BioSDOntoDiscoveringCache ();

		// We need both caches, because only OntoTermDiscoveryStoreCache will persist the disvovered terms
		ontoTermDisvoverer = new CachedOntoTermDiscoverer (  
			new CachedOntoTermDiscoverer ( newBaseDiscoverer (), biosdCache ),
			new OntoTermDiscoveryStoreCache ("")
		);
	}
	
	@After
	public void cleanUp ()
	{
		new Purger ().purge ( new DateTime ().minusMinutes ( 1 ).toDate (), new Date() );
	}
	
	/**
	 * Instantiates a {@link OntologyTermDiscoverer}, based on {@link #ONTO_DISCOVERER_PROP_NAME}. This is needed by
	 * some tests.
	 */
	public static OntologyTermDiscoverer newBaseDiscoverer ()
	{
		OntologyTermDiscoverer baseDiscoverer = null;
		String ontoDiscovererProp = System.getProperty ( ONTO_DISCOVERER_PROP_NAME, "zooma" );
		
		if ( "zooma".equalsIgnoreCase ( ontoDiscovererProp ) )
		{
			AbstractZOOMASearch zoomaClient = new ZOOMASearchClient ();
			//zoomaClient.setMinConfidence ( Confidence.fromScore ( 54d ) );
			baseDiscoverer = new ZoomaOntoTermDiscoverer ( zoomaClient );
		}
		else if ( "ols".equalsIgnoreCase ( ontoDiscovererProp ) )
		{
			OLSClient olsClient = AnnotatorResources.getInstance ().getOLSClient ();
			baseDiscoverer = new OLSOntoTermDiscoverer( olsClient );
		}
		else throw new IllegalArgumentException ( String.format ( 
			"Bad value '%s' for the property '%s'", ontoDiscovererProp, ONTO_DISCOVERER_PROP_NAME 
		));		
		
		return baseDiscoverer;
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
				
		XStopWatch timer = new XStopWatch ();
		
		// Annotate this property
		//
		timer.start ();
		List<DiscoveredTerm> terms = ontoTermDisvoverer.getOntologyTerms ( value, type );
		long time1 = timer.getTime ();
		
		assertTrue ( "No discovered terms!", terms.size () > 0 );
		
		log.info ( "Discovered entries:\n{}", terms.toString () );
		
		// Now save it to the DB and verify persistence
		//
		AnnotatorPersister persister = new AnnotatorPersister ();
		persister.persist ();
		
		EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
		EntityManager em = emf.createEntityManager ();

		String pvkey = ExpPropValAnnotation.getPvalText ( pval );

		// TODO: we need to move this on some DAO or alike
		Number savedAnnCt = (Number) em.createQuery ( 
			"SELECT COUNT ( DISTINCT pvann ) FROM ExpPropValAnnotation pvann WHERE sourceText = :sourceText" 
		)
		.setParameter ( "sourceText", pvkey )
		.getSingleResult ();

		assertEquals ( "Wrong size for saved terms!", terms.size (), savedAnnCt.intValue () );
		
		for ( DiscoveredTerm dtermi: terms )
		{
			String uri = dtermi.getIri ();
			ExpPropValAnnotation pvanndb = em.find ( ExpPropValAnnotation.class, new ExpPropValAnnotation.Key ( pvkey, uri ) );

			assertNotNull ( format ( "PV annotation not saved for %s:%s!", pvkey, uri ), pvanndb );
			assertEquals (
				format ( "Wrong annotation type for %s:%s!", pvkey, uri ),
				new OntoTermDiscoveryStoreCache("").getTypeMarker (), pvanndb.getType ()
			);
			assertEquals ( 
				format ( "Wrong annotation provenance for %s:%s!", pvkey, uri ),
				PropertyValAnnotationManager.PROVENANCE_MARKER, pvanndb.getProvenance () 
			);
		}	
		
		// Test that the BioSD cache is actually faster
		//
		log.debug ( "Testing calls to the loaded cache" );
		timer.restart ();
		for ( int i = 0; i < 100; i++ )
		{
			List<DiscoveredTerm> terms2 = biosdCache.getOntologyTerms ( value, type );
			log.trace ( "Call {}, time {}", i, timer.getTime () );
			assertEquals ( "Second call to the cache didn't work!", terms.size (), terms2.size () );
		}
		timer.stop ();
		
		double time2 = timer.getTime () / 100.0;
		
		log.info ( "Second-call versus first-call time: {}, {}", time2, time1 );
		assertTrue ( "WTH?! Second call time bigger than first!", time2 < time1 );
		
		em.close ();
	}
	
	/**
	* Tests that string values not related to ontologies are actually associated to an empty result.
	*/
	@SuppressWarnings ( "unchecked" )
	@Test
	public void testNullMapping ()
	{
		// Create the property
		//
		String value = "ghjgdfjh vfhvf4ui", type = "welnfkjwbf wgeuy32eghv";

		ExperimentalPropertyType ptype = new ExperimentalPropertyType ( type );
		ExperimentalPropertyValue<ExperimentalPropertyType> pval 
			= new ExperimentalPropertyValue<> ( value, ptype );
		
		List<DiscoveredTerm> terms = ontoTermDisvoverer.getOntologyTerms ( value, type );
		assertTrue ( "Non-empty result for discovered terms!", terms.isEmpty () );
		
		AnnotatorPersister persister = new AnnotatorPersister ();
		persister.persist ();
		
		EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
		EntityManager em = emf.createEntityManager ();

		String pvkey = ExpPropValAnnotation.getPvalText ( pval );

		// TODO: we need to move this on some DAO or alike
		Query query = em.createQuery (
				"FROM ExpPropValAnnotation pvann WHERE sourceText = :sourceText"
		)
				.setParameter ( "sourceText", pvkey );
		List<ExpPropValAnnotation> dbanns = query
		.getResultList ();

		assertEquals ( "Wrong size for saved terms!", 1, dbanns.size () );
		assertEquals ( "Saved annotation has a bad URI!",
			ExpPropValAnnotation.NULL_TERM_URI,
			dbanns.iterator ().next ().getOntoTermUri () 
		);
	}
	
}
