package uk.ac.ebi.fg.biosd.annotator.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotationManager;
import uk.ac.ebi.fg.biosd.annotator.model.ComputedOntoTerm;
import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.fg.biosd.annotator.model.ResolvedOntoTermAnnotation;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.OntoResolverAndAnnotator;
import uk.ac.ebi.fg.biosd.annotator.purge.Purger;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.xref.ReferenceSource;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>10 Sep 2015</dd>
 *
 */
public class AnnotatorAccessorTest
{
	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	@Test
	public void testGetExpPropValAnnotations ()
	{
		String value = "homo sapiens", type = "specie";

		ExperimentalPropertyType ptype = new ExperimentalPropertyType ( type );
		ExperimentalPropertyValue<ExperimentalPropertyType> pv = new ExperimentalPropertyValue<> ( value, ptype );

		// This is the annotation creation part, normally done by the annotator, during its periodic scheduled run
		PropertyValAnnotationManager annMgr = AnnotatorResources.getInstance ().getPvAnnMgr ();
		annMgr.annotate ( pv );
		new AnnotatorPersister ().persist ();
		
		
		// Now we read it, this is the part up to the clients (e.g. X-S)
		// 
		
		// This is the usual EMF configured by the BioSD loader (via hibernate.properties)
		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
		
		// This is the facade to get all info stored by the annotator
		// See also the hierarchy rooted at uk.ac.ebi.fg.biosd.annotator.model.FeatureAnnotation 
		AnnotatorAccessor annAccessor = new AnnotatorAccessor ( em );
		List<ExpPropValAnnotation> anns = annAccessor.getExpPropValAnnotations ( pv );
	
		boolean foundFlag = false;
		
		for ( ExpPropValAnnotation ann: anns )
		{
			// For the time being ZOOMA returns us only ontology term URIs (plus other details. such as the annotation 
			// confidence score
			// You can get them from ExpPropValAnnotation
			log.info ( String.format ( "Retrieved annotation for '%s': <%s>, score: %f", 
				ann.getSourceText (), ann.getOntoTermUri (), ObjectUtils.defaultIfNull ( ann.getScore (), 0 ) 
			));
			
			// And, if you don't care about all those details, you can get a traditional BioSD model representation
			// See also ExpPropValAnnotation.toOntologyEntries (), which iterates this method over a collection
			OntologyEntry oe = ann.asOntologyEntry ();
			
			log.info ( "Annotation for {} as ontology entry: ", pv, oe );

			// label is null, accession contains the URI, source is null
			if ( "http://purl.obolibrary.org/obo/NCBITaxon_9606".equals ( oe.getAcc () ) )
				foundFlag = true;
		}
			
		Assert.assertTrue ( "AnnotatorAccessor doesn't return stored pv annotation!", foundFlag );
	}
	
	
	@Test
	public void getResolvedOntoTerm ()
	{
		ExperimentalPropertyType ptype = new ExperimentalPropertyType ( "disease" );
		ExperimentalPropertyValue<ExperimentalPropertyType> pval = new ExperimentalPropertyValue<> ( "asthma", ptype );
		
		ReferenceSource src = new ReferenceSource ( "EFO", null );
		OntologyEntry oe = new OntologyEntry ( "0000270", src );
		oe.setLabel ( "Asthma Disease" );
		pval.addOntologyTerm ( oe );
		
		
		// Annotate and persist
		//
		OntoResolverAndAnnotator ontoAnnotator = new OntoResolverAndAnnotator ();
		ontoAnnotator.annotate ( pval );
		new AnnotatorPersister ().persist ();
		

		// Now we read it, this is the part up to the clients (e.g. X-S)
		//
		
		// This is the usual EMF configured by the BioSD loader (via hibernate.properties)
		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
		
		// This is the facade to get all info stored by the annotator
		// See also the hierarchy rooted at uk.ac.ebi.fg.biosd.annotator.model.FeatureAnnotation 
		AnnotatorAccessor annAccessor = new AnnotatorAccessor ( em );
		
		// This is the quick way to get the possibly new ontology entry that was obtained from the original parameter and
		// resolved via some OLS
		OntologyEntry newOe = annAccessor.getResolvedOntoTermAsOntologyEntry ( oe );
		
		// The method above returns the original oe if no resolver annotation exists for it
		assertNotSame ( "Resolver didn't work!", oe, newOe );
		
		// getAcc() contains the URI
		assertEquals ( "Got wrong URI from the accessor!", "http://www.ebi.ac.uk/efo/EFO_0000270", newOe.getAcc () );
		assertEquals ( "Got wrong label from the accessor!", "asthma", StringUtils.lowerCase ( newOe.getLabel () ) );
		
		
		
		// ----------------
		
		// This other calls are to get details about the OE resolving via ontology lookup servcice
		// 
		
		Pair<ComputedOntoTerm, ResolvedOntoTermAnnotation> resolvedResult = annAccessor.getResolvedOntoTerm ( oe );
		
		assertNotNull ( "OE Resolution didn't work!", resolvedResult );
				
		// ComputedOntoTerm contains details about the ontology term, as they were obtained by the OLS
		ComputedOntoTerm oeComp = resolvedResult.getLeft ();
		log.info ( "ComputedOntoTerm: {}", oeComp );
		assertEquals ( "Wrong URI from the accessor!", "http://www.ebi.ac.uk/efo/EFO_0000270", oeComp.getUri () );
		assertEquals ( "Wrong label fetched for the annotation!", "asthma", StringUtils.lowerCase ( oeComp.getLabel () ) ); 

		
		// And ResolvedOntoTermAnnotation contains data on the annotation and the initial ontology term it originated from
		ResolvedOntoTermAnnotation oeAnn = resolvedResult.getRight ();
		log.info ( "ResolvedOntoTermAnnotation: {}", oeAnn );
		assertEquals ( "Annotation and term have different URIs!", oeComp.getUri (), oeAnn.getOntoTermUri () );
	}

	@Before
	public void initResources () 
	{
		AnnotatorResources.reset ();
	}
	
	@After
	public void cleanUp ()
	{
		new Purger ().purge ( new DateTime ().minusMinutes ( 1 ).toDate (), new Date() );
	}	
}
