package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import javax.persistence.EntityManager;

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
		Table<Class, String, Object> store = AnnotatorResources.getInstance ().getNewStore ();

		ComputedOntoTerm oes = (ComputedOntoTerm) store.get ( 
			ComputedOntoTerm.class, "http://www.ebi.ac.uk/efo/EFO_0000270"
		);
		assertNotNull ( "Expected OntoTerm not created!", oes );
		
		ResolvedOntoTermAnnotation oeann = (ResolvedOntoTermAnnotation) store.get (
			ResolvedOntoTermAnnotation.class, ResolvedOntoTermAnnotation.getOntoEntryText ( oe ) 
		);
		
		assertNotNull ( "No annotations created!", oeann );

		// Verify persistence
		//
		AnnotatorPersister persister = new AnnotatorPersister ();
		persister.persist ();
		
		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();

		ComputedOntoTerm oedb = em.find ( ComputedOntoTerm.class, oes.getUri () );
		assertNotNull ( "Resolved ontology term not saved!", oedb );
		
		ResolvedOntoTermAnnotation oeanndb = em.find ( ResolvedOntoTermAnnotation.class, oeann.getSourceText () );
		assertNotNull ( "Ontology annotation not saved!", oeanndb );
		assertEquals ( "Wrong annotation type!", OntoResolverAndAnnotator.ANNOTATION_TYPE_MARKER, oeanndb.getType () );
		assertEquals ( "Wrong annotation provenance!", PropertyValAnnotationManager.PROVENANCE_MARKER, oeanndb.getProvenance () );
	}
}
