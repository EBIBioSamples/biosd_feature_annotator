package uk.ac.ebi.fg.biosd.annotator.datadiscover;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotationManager;
import uk.ac.ebi.fg.biosd.annotator.model.DataItem;
import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.fg.biosd.annotator.model.NumberItem;
import uk.ac.ebi.fg.biosd.annotator.persistence.AnnotatorPersister;
import uk.ac.ebi.fg.biosd.annotator.persistence.dao.DataItemDAO;
import uk.ac.ebi.fg.biosd.annotator.purge.Purger;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.expgraph.properties.Unit;
import uk.ac.ebi.fg.core_model.expgraph.properties.UnitDimension;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fgpt.zooma.model.AnnotationPrediction.Confidence;
import uk.ac.ebi.onto_discovery.api.OntologyTermDiscoverer.DiscoveredTerm;

import com.google.common.collect.Table;

/**
 * Test for {@link NumericalDataAnnotatorTest}.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>17 Dec 2014</dd>
 *
 */
public class NumericalDataAnnotatorTest
{
	private NumericalDataAnnotator numAnn;
	
	private Logger log = LoggerFactory.getLogger ( this.getClass () );

	
	private class MyPvMgr extends PropertyValAnnotationManager
	{
		protected MyPvMgr ( AnnotatorResources resources )
		{
			super ( resources );
			numAnn = this.ZoomaNumAnnotator;
		}
	}
	
	
	public NumericalDataAnnotatorTest () 
	{
		AnnotatorResources res = AnnotatorResources.getInstance ();
		res.getZoomaClient ().setMinConfidence ( Confidence.MEDIUM );
		new MyPvMgr ( res );
	}
	
	@Before
	public void initResources () {
		AnnotatorResources.getInstance ().reset ();
	}
	
	
	@Test
	@SuppressWarnings ( { "rawtypes", "unchecked" } )
	public void testNumberExtraction ()
	{
		// Create the property
		//
		ExperimentalPropertyType ptype = new ExperimentalPropertyType ( "Weight" );
		ExperimentalPropertyValue<ExperimentalPropertyType> pval = new ExperimentalPropertyValue<> ( "70", ptype );
		Unit unit = new Unit ( "kg", new UnitDimension ( "weight" ) );
		pval.setUnit ( unit );
		
		// Annotate
		assertTrue ( "Not recognised as number!", numAnn.annotate ( pval ) );
		
		// Verify
		//
		Table<Class, String, Object> store = AnnotatorResources.getInstance ().getStore ();
		DataItem dataItem = (DataItem) store.get ( DataItem.class, pval.getTermText () );
		assertNotNull ( "No data-item found in memory store!", dataItem );
		
		// Unit annotation
		List<DiscoveredTerm> uterms = (List<DiscoveredTerm>) store.get ( 
			DiscoveredTerm.class, ExpPropValAnnotation.getPvalText ( "Unit", unit.getTermText () ) 
		);
		
		assertNotNull ( "Unit not annotated!", uterms );
		assertTrue ( "Unit not annotated!", uterms.size () > 0 );

		log.info ( "Unit terms:\n{}", uterms );
		
		
		// Persist
		AnnotatorPersister persister = new AnnotatorPersister ();
		persister.persist ();
		
		// Verify persisted DI
		EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
		EntityManager em = emf.createEntityManager ();
		DataItemDAO didao = new DataItemDAO ( em );
		DataItem didb = didao.find ( dataItem );
		
		assertNotNull ( "Data Item not annotated!", didb );
		assertTrue ( "Saved Data Item is wrong!", didb instanceof NumberItem );
		NumberItem numdb = (NumberItem) didb;
		
		Assert.assertEquals ( "Saved Data Item has a wrong value!", 70, (double) numdb.getValue (), 1E-9 );
		
		// Purge
		Purger purger = new Purger ();
		purger.purge ( new DateTime ().minusMinutes ( 5 ).toDate (), new Date () );
		
		didao.setEntityManager ( em = emf.createEntityManager () );
		assertNull ( "Data item not deleted!", didao.find ( dataItem ) );
	}	
	
	
	@Test
	public void testDataItemReuse ()
	{
		ExperimentalPropertyType ptype1 = new ExperimentalPropertyType ( "Weight" );
		ExperimentalPropertyValue<ExperimentalPropertyType> pval1 = new ExperimentalPropertyValue<> ( "50", ptype1 );
		
		ExperimentalPropertyType ptype2 = new ExperimentalPropertyType ( "Age" );
		ExperimentalPropertyValue<ExperimentalPropertyType> pval2 = new ExperimentalPropertyValue<> ( "50", ptype2 );
		
		numAnn.annotate ( pval1 );
		numAnn.annotate ( pval2 );
		
		// Persist
		AnnotatorPersister persister = new AnnotatorPersister ();
		persister.persist ();

		// Verify there is only one DI
		EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
		EntityManager em = emf.createEntityManager ();
		DataItemDAO didao = new DataItemDAO ( em );

		NumberItem di = new NumberItem ( 50.0 );
		List<NumberItem> didbs = didao.find ( di, false, true );
		assertEquals ( "Wrong no of saved numbers!", 1, didbs.size () );
		
		// Purge
		Purger purger = new Purger ();
		purger.purge ( new DateTime ().minusMinutes ( 5 ).toDate (), new Date () );
		
		didao.setEntityManager ( em = emf.createEntityManager () );
		assertEquals ( "Data item not deleted!", 0, didao.find ( di, false, true ).size () );
	}
}
