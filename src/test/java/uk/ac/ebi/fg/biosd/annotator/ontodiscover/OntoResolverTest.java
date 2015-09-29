package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import javax.persistence.EntityManager;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotationManager;
import uk.ac.ebi.fg.biosd.annotator.model.ComputedOntoTerm;
import uk.ac.ebi.fg.biosd.annotator.model.ResolvedOntoTermAnnotation;
import uk.ac.ebi.fg.biosd.annotator.persistence.AnnotatorPersister;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.xref.ReferenceSource;

import com.google.common.collect.Table;

/**
 * TODO: Comment me!
 *
 * <dl><dt>date</dt><dd>21 Nov 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class OntoResolverTest
{
	@Before
	public void initResources () {
		AnnotatorResources.reset ();
	}

	@Test
	public void testBasics ()
	{
		ExperimentalPropertyType ptype = new ExperimentalPropertyType ( "disease" );
		ExperimentalPropertyValue<ExperimentalPropertyType> pval = new ExperimentalPropertyValue<> ( "asthma", ptype );
		
		ReferenceSource src = new ReferenceSource ( "EFO", null );
		OntologyEntry oe = new OntologyEntry ( "0000270", src );
		oe.setLabel ( "Asthma Disease" );
		pval.addOntologyTerm ( oe );
		
		
		// Annotate
		//
		OntoResolverAndAnnotator ontoAnnotator = new OntoResolverAndAnnotator ();
		ontoAnnotator.annotate ( pval );
		
		// Check
		//
		Table<Class, String, Object> store = AnnotatorResources.getInstance ().getStore ();

		ComputedOntoTerm oeComp = (ComputedOntoTerm) store.get ( 
			ComputedOntoTerm.class, "http://www.ebi.ac.uk/efo/EFO_0000270"
		);
		assertNotNull ( "Expected OntoTerm not created!", oeComp );
		
		ResolvedOntoTermAnnotation oeAnn = (ResolvedOntoTermAnnotation) store.get (
			ResolvedOntoTermAnnotation.class, ResolvedOntoTermAnnotation.getOntoEntryText ( oe ) 
		);

		assertNotNull ( "Annotation not created!", oeAnn );
		assertNotNull ( "Resolved term not created!", oeComp );
		assertEquals ( "Wrong label fetched for the annotation!", "asthma", StringUtils.lowerCase ( oeComp.getLabel () ) ); 
		assertEquals ( "Wrong URI fetched for the resolved term", "http://www.ebi.ac.uk/efo/EFO_0000270", oeComp.getUri () );
		assertEquals ( "Annotation and term have different URIs!", oeComp.getUri (), oeAnn.getOntoTermUri () );
		
		// Verify persistence
		//
		AnnotatorPersister persister = new AnnotatorPersister ();
		persister.persist ();
		
		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();

		ComputedOntoTerm oeCompDb = em.find ( ComputedOntoTerm.class, oeComp.getUri () );
		assertNotNull ( "Resolved ontology term not saved!", oeCompDb );
		
		ResolvedOntoTermAnnotation oeAnnDb = em.find ( ResolvedOntoTermAnnotation.class, oeAnn.getSourceText () );
		assertNotNull ( "Ontology annotation not saved!", oeAnnDb );
		assertEquals ( "Wrong annotation type!", OntoResolverAndAnnotator.ANNOTATION_TYPE_MARKER, oeAnnDb.getType () );
		assertEquals ( "Wrong annotation provenance!", PropertyValAnnotationManager.PROVENANCE_MARKER, oeAnnDb.getProvenance () );
	
		assertNotNull ( "Resolved term not created in the DB!", oeCompDb );
		assertEquals ( "Wrong label fetched for the annotation from DB!", "asthma", StringUtils.lowerCase ( oeCompDb.getLabel () ) ); 
		assertEquals ( "Wrong URI fetched for the resolved term from DB!", "http://www.ebi.ac.uk/efo/EFO_0000270", oeCompDb.getUri () );
		assertEquals ( "Annotation and term have different URIs in the DB!", oeCompDb.getUri (), oeAnnDb.getOntoTermUri () );
	}
}
