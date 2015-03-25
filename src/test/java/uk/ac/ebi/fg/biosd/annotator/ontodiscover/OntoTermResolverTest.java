package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import static org.apache.commons.lang3.StringUtils.contains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import org.junit.Assert;
import org.junit.Test;

import uk.ac.ebi.fg.biosd.annotator.persistence.BatchTransactionManager;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.terms.OntologyEntryDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.toplevel.Annotation;
import uk.ac.ebi.fg.core_model.toplevel.TextAnnotation;
import uk.ac.ebi.fg.core_model.xref.ReferenceSource;

/**
 * TODO: Comment me!
 *
 * <dl><dt>date</dt><dd>21 Nov 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class OntoTermResolverTest
{
	@Test
	public void testBasics ()
	{
		ExperimentalPropertyType ptype = new ExperimentalPropertyType ( "disease" );
		ExperimentalPropertyValue<ExperimentalPropertyType> pval = new ExperimentalPropertyValue<> ( "asthma", ptype );
		
		ReferenceSource src = new ReferenceSource ( "EFO", null );
		OntologyEntry oe = new OntologyEntry ( "0000270", src );
		oe.setLabel ( "Asthma Disease" );
		pval.addOntologyTerm ( oe );
		
		
		
		EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();

		EntityManager em = emf.createEntityManager ();
		EntityTransaction tx = em.getTransaction ();
		tx.begin ();
		em.persist ( pval );
		tx.commit ();
		em.close ();

		
		// Annotate
		//
		OntoTermResolverAndAnnotator ontoAnnotator = new OntoTermResolverAndAnnotator ();

		BatchTransactionManager btm = BatchTransactionManager.getThreadLocalInstance ();
		btm.begin ();
		ontoAnnotator.annotate ( pval );
		btm.commit ( true );
		
		
		// Check
		//
		OntologyEntryDAO<OntologyEntry> oedao = new OntologyEntryDAO<> ( OntologyEntry.class, em = emf.createEntityManager () );
		OntologyEntry oedb = oedao.find ( oe.getId () );
		
		assertEquals ( "Accession wasn't set with the right URI!", "http://www.ebi.ac.uk/efo/EFO_0000270", oedb.getAcc () );
		assertFalse ( "No annotations saved!", oedb.getAnnotations () == null || oedb.getAnnotations ().isEmpty () );
		TextAnnotation foundAnn = null;
		TextAnnotation marker = OntoTermResolverAndAnnotator.createOntoResolverMarker ( oe.getAcc (), src.getAcc (), oe.getLabel () );
		for ( Annotation ann: oedb.getAnnotations () )
		{
			if ( ! ( ann instanceof TextAnnotation ) ) continue;
			
			TextAnnotation tann = ( (TextAnnotation) ann );
			if ( tann.getType () == null || !marker.getType ().getName ().equals ( tann.getType ().getName () ) ) continue;
			
			if ( !contains ( tann.getText (), oe.getAcc () ) ) continue;
			if ( !contains ( tann.getText (), oe.getLabel () ) ) continue;
			if ( !contains ( tann.getText (), src.getAcc () ) ) continue;
			
			foundAnn = tann;
			break;
		}

		Assert.assertTrue ( "No Ontology Discoverer Annotation found!", foundAnn != null );

		// Remove
		tx = em.getTransaction ();
		tx.begin ();
		
		em.createNativeQuery ( "DELETE FROM exp_prop_val_onto_entry WHERE owner_id=:pvid AND oe_id = :oeid" )
			.setParameter ( "pvid", pval.getId () )
			.setParameter ( "oeid", oedb.getId () )
			.executeUpdate ();
		
		em.createQuery ( "DELETE FROM ExperimentalPropertyValue WHERE id = " + pval.getId () ).executeUpdate ();
		
		em.createNativeQuery ( "DELETE FROM onto_entry_annotation WHERE owner_id=:oeid AND annotation_id = :annid" )
			.setParameter ( "oeid", oedb.getId () )
			.setParameter ( "annid", foundAnn.getId () )
			.executeUpdate ();
		
		em.remove ( foundAnn );
		em.remove ( oedb );
		tx.commit ();
		em.close ();

	}
}
