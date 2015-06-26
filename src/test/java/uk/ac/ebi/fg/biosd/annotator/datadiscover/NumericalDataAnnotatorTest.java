package uk.ac.ebi.fg.biosd.annotator.datadiscover;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.model.DataItem;
import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.OntoTermDiscoveryStoreCache;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.ZOOMAUnitSearch;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.expgraph.properties.Unit;
import uk.ac.ebi.fg.core_model.expgraph.properties.UnitDimension;
import uk.ac.ebi.fgpt.zooma.search.ZOOMASearchClient;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.CachedOntoTermDiscoverer;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer.DiscoveredTerm;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.ZoomaOntoTermDiscoverer;

import com.google.common.collect.Table;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>17 Dec 2014</dd>
 *
 */
public class NumericalDataAnnotatorTest
{
	// TODO: re-enable BioSD Cache
//	private NumericalDataAnnotator numAnn = 
//		new NumericalDataAnnotator (
//			new BioSDCachedOntoTermDiscoverer ( 
//				new CachedOntoTermDiscoverer ( 
//					new ZoomaOntoTermDiscoverer ( new ZOOMAUnitSearch ( new ZOOMASearchClient () ), 50f ),
//					new BioSDOntoDiscoveringCache ()
//				),
//				new OntoTermDiscoveryMemCache ( AnnotatorResources.getInstance ().getOntoTerms () )	
//			)
//		);
	
	private NumericalDataAnnotator numAnn = 
		new NumericalDataAnnotator (
			new CachedOntoTermDiscoverer ( 
				new ZoomaOntoTermDiscoverer ( new ZOOMAUnitSearch ( new ZOOMASearchClient () ), 50f ),
				new OntoTermDiscoveryStoreCache ()
			)
		);
	

	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	@Before
	public void initResources () {
		AnnotatorResources.reset ();
	}
	
	
	@Test
	@SuppressWarnings ( "rawtypes" )
	public void testNumberExtraction ()
	{
		// Create the property
		//
		ExperimentalPropertyType ptype = new ExperimentalPropertyType ( "Weight" );
		ExperimentalPropertyValue<ExperimentalPropertyType> pval = new ExperimentalPropertyValue<> ( "70", ptype );
		Unit unit = new Unit ( "Kg", new UnitDimension ( "weight" ) );
		pval.setUnit ( unit );
		
		// Annotate
		assertTrue ( "Not recognised as number!", numAnn.annotate ( pval ) );
		
		// Verify
		//
		Table<Class, String, Object> store = AnnotatorResources.getInstance ().getNewStore ();
		DataItem dataItem = (DataItem) store.get ( DataItem.class, pval.getTermText () );
		assertFalse ( "No data-item found in memory store!", dataItem == null );
		
		// Unit annotation
		List<DiscoveredTerm> uterms = (List<DiscoveredTerm>) store.get ( DiscoveredTerm.class, ExpPropValAnnotation.getPvalText ( null, unit.getTermText () ) );
		
		assertNotNull ( "Unit not annotated!", uterms );
		assertTrue ( "Unit not annotated!", uterms.size () > 0 );

		log.info ( "Unit terms:\n{}", uterms );
		
		
		// TODO:
		// Persist
		// Verify persisted DI and its annotation
		// Purge
	}	
	
	
//	@Test
//	@SuppressWarnings ( "rawtypes" )
//	public void testDataItemReuse ()
//	{
//		ExperimentalPropertyType ptype1 = new ExperimentalPropertyType ( "Weight" );
//		ExperimentalPropertyValue<ExperimentalPropertyType> pval1 = new ExperimentalPropertyValue<> ( "50", ptype1 );
//		
//		ExperimentalPropertyType ptype2 = new ExperimentalPropertyType ( "Age" );
//		ExperimentalPropertyValue<ExperimentalPropertyType> pval2 = new ExperimentalPropertyValue<> ( "50", ptype2 );
//		
//		EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
//		EntityManager em = emf.createEntityManager ();
//		
//		EntityTransaction tx = em.getTransaction ();
//		tx.begin ();
//		em.persist ( pval1 );
//		em.persist ( pval2 );
//		tx.commit ();
//		em.close ();
//
//		// Annotate
//		assertTrue ( "pv1 not recognised as number!", numAnn.annotate ( pval1 ) );
//		assertTrue ( "pv1 not recognised as number!", numAnn.annotate ( pval2 ) );
//		
//		// Persist
//		AnnotatorResources.getInstance ().getStore ().find ( pval1, pval1.getId ().toString () );
//		AnnotatorResources.getInstance ().getStore ().find ( pval2, pval2.getId ().toString () );
//		AnnotatorPersister persister = new AnnotatorPersister ();
//		persister.persist ();
//
//		
//		// Verify
//		em = emf.createEntityManager ();
//		AnnotatableDAO<ExperimentalPropertyValue> pvdao = new AnnotatableDAO<> ( ExperimentalPropertyValue.class, em );
//		ExperimentalPropertyValue<?> pval1db = pvdao.find ( pval1.getId () );
//		ExperimentalPropertyValue<?> pval2db = pvdao.find ( pval2.getId () );
//
//		NumberItem ni1 = (NumberItem) pval1db.getDataItems ().iterator ().next ();
//		NumberItem ni2 = (NumberItem) pval2db.getDataItems ().iterator ().next ();
//		
//		assertEquals ( "Data Item wasn't reused!", ni1.getId (), ni2.getId () );
//		
//		
//		// Clean-up
//		//
//		tx = em.getTransaction ();
//		tx.begin ();
//		em.remove ( pval1db );
//		em.remove ( pval2db );
//		tx.commit ();
//		
//		tx.begin ();
//		assertTrue ( "DataItems not deleted!", new DataItemUnloadingUnlistener ( em ).postRemoveGlobally () > 0);
//		assertTrue ( "Annotations not deleted!", new AnnotationUnloaderListener ( em ).postRemoveGlobally () > 0);
//		tx.commit ();
//		
//		em.close ();
//		
//	}
}
