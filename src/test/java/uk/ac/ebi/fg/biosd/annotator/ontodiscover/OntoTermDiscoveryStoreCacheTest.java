package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.ZoomaOntoTermDiscoverer;
import uk.ac.ebi.onto_discovery.api.OntologyTermDiscoverer.DiscoveredTerm;
import uk.ac.ebi.utils.time.XStopWatch;

import com.google.common.collect.Table;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>26 Jun 2015</dd>
 *
 */
public class OntoTermDiscoveryStoreCacheTest
{
	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	@Before
	public void initResources () {
		AnnotatorResources.reset ();
	}
	

	@Test
	public void testBasics ()
	{
		OntoDiscoveryAndAnnotator ontoDiscoverer = new OntoDiscoveryAndAnnotator (
			new BioSDCachedOntoTermDiscoverer ( // 1st level, Memory Cache
				new ZoomaOntoTermDiscoverer ( AnnotatorResources.getInstance ().getZoomaClient (), 54.0f ),
				new OntoTermDiscoveryStoreCache ()
			)
		);
	
		String value = "homo sapiens", type = "specie";
	
		ExperimentalPropertyType ptype = new ExperimentalPropertyType ( type );
		ExperimentalPropertyValue<ExperimentalPropertyType> pval 
			= new ExperimentalPropertyValue<> ( value, ptype );

		XStopWatch timer = new XStopWatch ();
		timer.start ();
		ontoDiscoverer.annotate ( pval, false );
		timer.stop ();
		
		long firstCallTime = timer.getTime ();
		
		
		// Verify
		String pvkey = ExpPropValAnnotation.getPvalText ( pval );
		
		Table<Class, String, Object> store = AnnotatorResources.getInstance ().getStore ();
		List<DiscoveredTerm> dterms = (List<DiscoveredTerm>) store.get ( DiscoveredTerm.class, pvkey );
		assertNotNull ( "ZOOMA terms not found in store", dterms );
		assertTrue ( "dterms is empty!", dterms.size () > 0 );

		log.info ( "Discovered terms:\n{}", dterms );
		
		
		// Verify the cache
		timer.resumeOrStart ();
		for ( int i = 0; i < 100; i++ )
			ontoDiscoverer.annotate ( pval, false );
		timer.stop ();
		
		double nextCallsTime = timer.getTime () / 100.0;
		
		log.info ( "First call time: {} ms, average time subsequent calls: {} ms", firstCallTime, nextCallsTime );
		
		assertTrue ( "WTH?! Time after first call is longer!", nextCallsTime < firstCallTime / 100.0 );
	}
}
