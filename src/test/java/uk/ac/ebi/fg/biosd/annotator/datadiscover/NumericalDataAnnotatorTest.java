package uk.ac.ebi.fg.biosd.annotator.datadiscover;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.util.Date;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.ontodiscover.BioSDCachedOntoTermDiscoverer;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.BioSDOntoDiscoveringCache;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.ZOOMAUnitSearch;
import uk.ac.ebi.fg.biosd.annotator.purge.Purger;
import uk.ac.ebi.fg.biosd.sampletab.persistence.entity_listeners.expgraph.properties.UnitUnloadingListener;
import uk.ac.ebi.fg.biosd.sampletab.persistence.entity_listeners.expgraph.properties.dataitems.DataItemUnloadingUnlistener;
import uk.ac.ebi.fg.biosd.sampletab.persistence.entity_listeners.toplevel.AnnotationUnloaderListener;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.expgraph.properties.Unit;
import uk.ac.ebi.fg.core_model.expgraph.properties.UnitDimension;
import uk.ac.ebi.fg.core_model.expgraph.properties.dataitems.DataItem;
import uk.ac.ebi.fg.core_model.expgraph.properties.dataitems.NumberItem;
import uk.ac.ebi.fg.core_model.expgraph.properties.dataitems.ValueItem;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AnnotatableDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.toplevel.Annotation;
import uk.ac.ebi.fg.core_model.toplevel.TextAnnotation;
import uk.ac.ebi.fgpt.zooma.search.ZOOMASearchClient;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.CachedOntoTermDiscoverer;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.ZoomaOntoTermDiscoverer;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>17 Dec 2014</dd>
 *
 */
public class NumericalDataAnnotatorTest
{
	private NumericalDataAnnotator numAnn = 
		new NumericalDataAnnotator (
			new BioSDCachedOntoTermDiscoverer ( 
				new CachedOntoTermDiscoverer ( 
					new ZoomaOntoTermDiscoverer ( 
						new ZOOMAUnitSearch ( new ZOOMASearchClient () ), 
						50f 
					)
				),
				new BioSDOntoDiscoveringCache ()
			)
		);

	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	
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
		
		EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
		EntityManager em = emf.createEntityManager ();
		
		EntityTransaction tx = em.getTransaction ();
		tx.begin ();
		em.persist ( pval );
		tx.commit ();

		// Annotate
		//
		
		assertTrue ( "Not recognised as number!", numAnn.annotate ( pval, em ) );
		em.close ();

		// Verify
		//
		em = emf.createEntityManager ();
		AnnotatableDAO<ExperimentalPropertyValue> pvdao = new AnnotatableDAO<> ( ExperimentalPropertyValue.class, em );
		ExperimentalPropertyValue<?> pvaldb = pvdao.find ( pval.getId () );
		assertFalse ( "No data-item found!", pvaldb.getDataItems ().isEmpty () );
		

		TextAnnotation annMarker = NumericalDataAnnotator.createDataAnnotatorMarker ();
		long now = System.currentTimeMillis ();
		
		for ( DataItem dataItem: pvaldb.getDataItems () )
		{
			log.info ( "Data Item found: {}", dataItem );
			int nanns = 0;

			for ( Annotation ann: dataItem.getAnnotations () )
			{
				if ( ! ( ann instanceof TextAnnotation ) ) continue;
				TextAnnotation tann = (TextAnnotation) ann;
				
				log.info ( "-- Annotation found for data item: {}", ann );
				
				if ( annMarker.getText ().equals ( tann.getText () ) 
						 && annMarker.getProvenance ().equals ( tann.getProvenance () )
						 && ( now - tann.getTimestamp ().getTime () < 30 * 1000 )
						)
					nanns++; 
			}
			assertTrue ( "No data-item annotation found!", nanns > 0 );
		}

	
		
		// Check the unit
		Set<OntologyEntry> uoes = pvaldb.getUnit ().getOntologyTerms ();
		assertFalse ( "Argh! No ontology term saved for the Unit!", uoes.isEmpty () );
		
		for ( OntologyEntry uoe: uoes )
			log.info ( "Ontology term for the Unit: {}", uoe );
		
		
		// Clean-up
		//
		
		// Annotations about units 
		int deleted = new Purger ().purge ( new DateTime ().minusMinutes ( 1 ).toDate (), new Date () );
		assertTrue ( "Annotations not deleted!", deleted > 0 );

		tx = em.getTransaction ();
		tx.begin ();
		em.remove ( pvaldb );
		tx.commit ();
		
		tx.begin ();
		assertTrue ( "Unit not deleted!", new UnitUnloadingListener ( em ).postRemoveGlobally () > 0);
		assertTrue ( "DataItems not deleted!", new DataItemUnloadingUnlistener ( em ).postRemoveGlobally () > 0);
		assertTrue ( "Annotations not deleted!", new AnnotationUnloaderListener ( em ).postRemoveGlobally () > 0);
		tx.commit ();
		
		em.close ();
	}	
	
	@SuppressWarnings ( "rawtypes" )
	@Test
	public void testDataItemReuse ()
	{
		ExperimentalPropertyType ptype1 = new ExperimentalPropertyType ( "Weight" );
		ExperimentalPropertyValue<ExperimentalPropertyType> pval1 = new ExperimentalPropertyValue<> ( "70", ptype1 );
		
		ExperimentalPropertyType ptype2 = new ExperimentalPropertyType ( "Age" );
		ExperimentalPropertyValue<ExperimentalPropertyType> pval2 = new ExperimentalPropertyValue<> ( "70", ptype2 );
		
		EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
		EntityManager em = emf.createEntityManager ();
		
		EntityTransaction tx = em.getTransaction ();
		tx.begin ();
		em.persist ( pval1 );
		em.persist ( pval2 );
		tx.commit ();

		// Annotate
		//
		
		assertTrue ( "pv1 not recognised as number!", numAnn.annotate ( pval1, em ) );
		assertTrue ( "pv1 not recognised as number!", numAnn.annotate ( pval2, em ) );
		em.close ();

		// Verify
		em = emf.createEntityManager ();
		AnnotatableDAO<ExperimentalPropertyValue> pvdao = new AnnotatableDAO<> ( ExperimentalPropertyValue.class, em );
		ExperimentalPropertyValue<?> pval1db = pvdao.find ( pval1.getId () );
		ExperimentalPropertyValue<?> pval2db = pvdao.find ( pval2.getId () );

		NumberItem ni1 = (NumberItem) pval1db.getDataItems ().iterator ().next ();
		NumberItem ni2 = (NumberItem) pval2db.getDataItems ().iterator ().next ();
		
		assertEquals ( "Data Item wasn't reused!", ni1.getId (), ni2.getId () );
		
		
		// Clean-up
		//
		tx = em.getTransaction ();
		tx.begin ();
		em.remove ( pval1db );
		em.remove ( pval2db );
		tx.commit ();
		
		tx.begin ();
		assertTrue ( "DataItems not deleted!", new DataItemUnloadingUnlistener ( em ).postRemoveGlobally () > 0);
		assertTrue ( "Annotations not deleted!", new AnnotationUnloaderListener ( em ).postRemoveGlobally () > 0);
		tx.commit ();
		
		em.close ();
		
	}
}
