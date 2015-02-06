package uk.ac.ebi.fg.biosd.annotator.ontodiscover;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import org.joda.time.DateTime;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotationManager;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.BioSDOntoDiscoveringCache;
import uk.ac.ebi.fg.biosd.annotator.purge.Purger;
import uk.ac.ebi.fg.biosd.annotator.purge.ZoomaAnnotationsPurger;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AnnotatableDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.toplevel.Annotation;
import uk.ac.ebi.fg.core_model.toplevel.TextAnnotation;
import uk.ac.ebi.fg.core_model.utils.toplevel.AnnotationUtils;
import uk.ac.ebi.fgpt.zooma.search.ZOOMASearchClient;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.CachedOntoTermDiscoverer;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.ZoomaOntoTermDiscoverer;

/**
 * Tests the {@link PropertyValAnnotationManager}.
 *
 * <dl><dt>date</dt><dd>2 Sep 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class OntoDiscoveryTest
{
	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	/**
	 * Basic test, with a single created {@link ExperimentalPropertyValue}.
	 */
	@Test
	@SuppressWarnings ( "rawtypes" )
	public void testAnnotator ()
	{
		// Create the property
		//
		ExperimentalPropertyType ptype = new ExperimentalPropertyType ( "specie" );
		ExperimentalPropertyValue<ExperimentalPropertyType> pval = new ExperimentalPropertyValue<> ( "homo sapiens", ptype );
		
		EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
		
		EntityManager em = emf.createEntityManager ();
		
		EntityTransaction tx = em.getTransaction ();
		tx.begin ();
		em.persist ( pval );
		tx.commit ();

		// Annotate
		//
		OntoDiscoveryAndAnnotator ontoDiscoverer = new OntoDiscoveryAndAnnotator (
			new CachedOntoTermDiscoverer ( 
				new CachedOntoTermDiscoverer ( 
					new ZoomaOntoTermDiscoverer ( new ZOOMASearchClient (), 50f ),
					new BioSDOntoDiscoveringCache ()
				)
			)
		);

		ontoDiscoverer.annotate ( pval, false, em );
		em = emf.createEntityManager ();

		// Verify
		//
		AnnotatableDAO<ExperimentalPropertyValue> pvdao = new AnnotatableDAO<> ( ExperimentalPropertyValue.class, em );
		ExperimentalPropertyValue<?> pvaldb = pvdao.find ( pval.getId () );

		Set<OntologyEntry> oes = pvaldb.getOntologyTerms ();
		assertFalse ( "Argh! No ontology term saved!", oes.isEmpty () );
		
		boolean hasZooma = false;
		TextAnnotation zoomaMarker = BioSDOntoDiscoveringCache.createZOOMAMarker ( pval.getTermText (), ptype.getTermText () );

		long now = System.currentTimeMillis (); 
		int nelems = 0;
		
		for ( OntologyEntry oe: oes )
		{
			nelems++;
			for ( Annotation ann: oe.getAnnotations () )
			{
				if ( ! ( ann instanceof TextAnnotation ) ) continue;
				TextAnnotation tann = (TextAnnotation) ann;
				
				log.info ( "Annotation found: {} for {}", tann, oe );
				
				if ( zoomaMarker.getText ().equals ( tann.getText () ) 
						 && zoomaMarker.getProvenance ().equals ( tann.getProvenance () )
						 && ( now - tann.getTimestamp ().getTime () < 30 * 1000 )
						)
				{
					hasZooma = true;
					nelems += 3; // corresponds to the ann, the link to pv and the link from oe
				}
			}
		}
	
		assertTrue ( "No ZOOMA annotation found!", hasZooma );
	
		// Clean-up
		int deleted = new Purger ().purge ( new DateTime ().minusMinutes ( 1 ).toDate (), new Date() );
		assertEquals ( "Annotations not deleted!", nelems, deleted );
		
		tx = em.getTransaction ();
		tx.begin ();
		em.remove ( pvaldb );
		tx.commit ();
		em.close ();
	}
	
	/**
	 * test with number flag, it should check the type only.
	 */
	@Test
	@SuppressWarnings ( "rawtypes" )
	public void testAnnotatorWithNumberFlag ()
	{
		// Create the property
		//
		ExperimentalPropertyType ptype = new ExperimentalPropertyType ( "Weight" );
		ExperimentalPropertyValue<ExperimentalPropertyType> pval = new ExperimentalPropertyValue<> ( "70 Kg", ptype );
		
		EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
		
		EntityManager em = emf.createEntityManager ();
		
		EntityTransaction tx = em.getTransaction ();
		tx.begin ();
		em.persist ( pval );
		tx.commit ();

		// Annotate
		//
		OntoDiscoveryAndAnnotator ontoDiscoverer = new OntoDiscoveryAndAnnotator (
			new CachedOntoTermDiscoverer ( 
				new CachedOntoTermDiscoverer ( 
					new ZoomaOntoTermDiscoverer ( new ZOOMASearchClient (), 50f ),
					new BioSDOntoDiscoveringCache ()
				)
			)
		);

		ontoDiscoverer.annotate ( pval, true, em );
		em = emf.createEntityManager ();

		// Verify
		//
		AnnotatableDAO<ExperimentalPropertyValue> pvdao = new AnnotatableDAO<> ( ExperimentalPropertyValue.class, em );
		ExperimentalPropertyValue<?> pvaldb = pvdao.find ( pval.getId () );

		Set<OntologyEntry> oes = pvaldb.getOntologyTerms ();
		assertFalse ( "Argh! No ontology term saved!", oes.isEmpty () );
		
		boolean hasZooma = false;
		TextAnnotation zoomaMarker = BioSDOntoDiscoveringCache.createZOOMAMarker ( ptype.getTermText (), "" );

		long now = System.currentTimeMillis (); 
		int nelems = 0;
		
		for ( OntologyEntry oe: oes )
		{
			nelems++;
			for ( Annotation ann: oe.getAnnotations () )
			{
				if ( ! ( ann instanceof TextAnnotation ) ) continue;
				TextAnnotation tann = (TextAnnotation) ann;
				
				log.info ( "Annotation found: {} for {}", tann, oe );
				
				if ( zoomaMarker.getText ().equals ( tann.getText () ) 
						 && zoomaMarker.getProvenance ().equals ( tann.getProvenance () )
						 && ( now - tann.getTimestamp ().getTime () < 30 * 1000 )
						)
				{
					hasZooma = true;
					nelems += 3; // corresponds to the ann, the link to pv and the link from oe
				}
			}
		}
	
		assertTrue ( "No ZOOMA annotation found!", hasZooma );
	
		// Clean-up
		int deleted = new Purger ().purge ( new DateTime ().minusMinutes ( 1 ).toDate (), new Date() );
		assertEquals ( "Annotations not deleted!", nelems, deleted );
		
		tx = em.getTransaction ();
		tx.begin ();
		em.remove ( pvaldb );
		tx.commit ();
		em.close ();
	}
	
	/**
	 * Checks the case that a property value isn't mapped to an ontology term, verifies that an annotation is added
	 * to the pv, which track the situation.
	 */
	@SuppressWarnings ( "rawtypes" )
	@Test
	public void testNoMapping ()
	{
		// Create the property
		//
		ExperimentalPropertyType ptype = new ExperimentalPropertyType ( "Bla Bla Bla Foo Silly" );
		ExperimentalPropertyValue<ExperimentalPropertyType> pval = new ExperimentalPropertyValue<> ( "xyz 1234", ptype );
		
		EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
		
		EntityManager em = emf.createEntityManager ();
		
		EntityTransaction tx = em.getTransaction ();
		tx.begin ();
		em.persist ( pval );
		tx.commit ();
		
		// Annotate
		//
		OntoDiscoveryAndAnnotator ontoDiscoverer = new OntoDiscoveryAndAnnotator (
			new CachedOntoTermDiscoverer ( 
				new CachedOntoTermDiscoverer ( 
					new ZoomaOntoTermDiscoverer ( new ZOOMASearchClient (), 50f ),
					new BioSDOntoDiscoveringCache ()
				)
			)
		);
		
		ontoDiscoverer.annotate ( pval, false, em );
		
		em.close ();
		em = emf.createEntityManager ();

		// Verify
		//
		AnnotatableDAO<ExperimentalPropertyValue> pvdao = new AnnotatableDAO<> ( ExperimentalPropertyValue.class, em );
		ExperimentalPropertyValue<?> pvaldb = pvdao.find ( pval.getId () );
		
		TextAnnotation zoomaEmptyMappingMarker = OntoDiscoveryAndAnnotator.createEmptyZoomaMappingMarker ();
		
		List<Annotation> anns = AnnotationUtils.find ( 
			pval.getAnnotations (), null, zoomaEmptyMappingMarker.getType ().getName (), false, true 
		);

		assertEquals ( "No-ontology marker not created!", 1, anns.size () );
		
		long annId = anns.iterator ().next ().getId ();
				
		tx = em.getTransaction ();
		tx.begin ();
		em.remove ( pvaldb );
		tx.commit ();
		em.close ();
		
		ZoomaAnnotationsPurger purger = new ZoomaAnnotationsPurger ();
		purger.purge ( new DateTime ().minusMinutes ( 1 ).toDate (), new Date() );
		
		em = emf.createEntityManager ();
		
		assertNull ( "The No-ontology marker is still here!", em.find ( TextAnnotation.class, annId ) );
		em.close ();
	}
}
