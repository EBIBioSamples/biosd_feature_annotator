package uk.ac.ebi.fg.biosd.annotator;


import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotator;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.BioSDOntoDiscoveringCache;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AnnotatableDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.toplevel.Annotation;
import uk.ac.ebi.fg.core_model.toplevel.TextAnnotation;

/**
 * TODO: Comment me!
 *
 * <dl><dt>date</dt><dd>2 Sep 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class PropertyValAnnotatorTest
{
	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	
	@Test
	@SuppressWarnings ( "rawtypes" )
	public void testAnnotator ()
	{
		ExperimentalPropertyType ptype = new ExperimentalPropertyType ( "specie" );
		ExperimentalPropertyValue<ExperimentalPropertyType> pval = new ExperimentalPropertyValue<> ( "homo sapiens", ptype );
		
		EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
		
		EntityManager em = emf.createEntityManager ();
		
		EntityTransaction tx = em.getTransaction ();
		tx.begin ();
		em.persist ( pval );
		tx.commit ();

		
		PropertyValAnnotator annotator = new PropertyValAnnotator ( 50f );
		assertTrue ( "The annotator returns false!", annotator.annotate ( pval.getId () ) );
		em = emf.createEntityManager ();

		
		AnnotatableDAO<ExperimentalPropertyValue> pvdao = new AnnotatableDAO<> ( ExperimentalPropertyValue.class, em );
		ExperimentalPropertyValue<?> pvaldb = pvdao.find ( pval.getId () );

		Set<OntologyEntry> oes = pvaldb.getOntologyTerms ();
		assertFalse ( "Argh! No ontology term saved!", oes.isEmpty () );
		
		boolean hasZooma = false;
		TextAnnotation zoomaMarker = BioSDOntoDiscoveringCache.createZOOMAMarker ( pval.getTermText (), ptype.getTermText () );

		long now = System.currentTimeMillis (); 
		
		for ( OntologyEntry oe: oes )
		{
			for ( Annotation ann: oe.getAnnotations () )
			{
				if ( ! ( ann instanceof TextAnnotation ) ) continue;
				TextAnnotation tann = (TextAnnotation) ann;
				
				log.info ( "Annotation found: {} for {}", tann, oe );
				
				if ( zoomaMarker.getText ().equals ( tann.getText () ) 
						 && zoomaMarker.getProvenance ().equals ( tann.getProvenance () )
						 && ( now - tann.getTimestamp ().getTime () < 30 * 1000 )
				) hasZooma = true;
			}
		}
	
		assertTrue ( "No ZOOMA annotation foud!", hasZooma );
	
	
		// Clean-up. TODO: use DAOs
		
		tx = em.getTransaction ();
		tx.begin ();
		for ( OntologyEntry oe: oes )
		{
			for ( Annotation ann: oe.getAnnotations () )
				em.remove ( ann );
			em.remove ( oe );
		}
		em.remove ( pvaldb );
		tx.commit ();
	}
	
}