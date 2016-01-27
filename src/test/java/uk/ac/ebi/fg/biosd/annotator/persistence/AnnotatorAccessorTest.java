package uk.ac.ebi.fg.biosd.annotator.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotationManager;
import uk.ac.ebi.fg.biosd.annotator.model.ComputedOntoTerm;
import uk.ac.ebi.fg.biosd.annotator.model.DataItem;
import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.fg.biosd.annotator.model.NumberItem;
import uk.ac.ebi.fg.biosd.annotator.model.ResolvedOntoTermAnnotation;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.OntoResolverAndAnnotator;
import uk.ac.ebi.fg.biosd.annotator.purge.Purger;
import uk.ac.ebi.fg.biosd.annotator.test.AnnotatorResourcesResetRule;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.expgraph.properties.Unit;
import uk.ac.ebi.fg.core_model.expgraph.properties.UnitDimension;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.xref.ReferenceSource;

/**
 * Tests for the {@link AnnotatorAccessor}. Have a look at the code here to get an idea of it works.
 * If you're looking for examples on how to use this class, have a look at {@link #testAll()}. 
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>10 Sep 2015</dd>
 *
 */
public class AnnotatorAccessorTest
{
	@Rule
	public TestRule resResetRule = new AnnotatorResourcesResetRule ();

	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	@Test
	public void testAll ()
	{
		// ---- First let's create some examples, see below for the code relevant to AnnotatorAccessor
		// 
		ExperimentalPropertyType ptype = new ExperimentalPropertyType ( "disease" );
		ExperimentalPropertyValue<ExperimentalPropertyType> pval = new ExperimentalPropertyValue<> ( "asthma", ptype );
		
		ReferenceSource src = new ReferenceSource ( "MESH", null );
		OntologyEntry oe = new OntologyEntry ( "D001249", src );
		pval.addOntologyTerm ( oe );

		// A numeric value with a unit
		ExperimentalPropertyValue<ExperimentalPropertyType> pvtemp = 
			new ExperimentalPropertyValue<ExperimentalPropertyType> ( "120", new ExperimentalPropertyType ( "Treatment Temperature" ) );
		pvtemp.setUnit ( new Unit ( "degree Celsius", new UnitDimension ( "Temperature" ) ) );
		
		// This is the annotation creation part, normally done by the annotator, during its periodic scheduled run
		// Keep going down
		PropertyValAnnotationManager annMgr = AnnotatorResources.getInstance ().getPvAnnMgr ();
		annMgr.annotate ( pval );
		annMgr.annotate ( pvtemp );
		new AnnotatorPersister ().persist ();

		// Do this in the unlikely case you first annotated, then use the access API. The annotator leaves around resources
		// that the accessor shouldn't see.
		AnnotatorResources.getInstance ().reset ();
		
		
		// ----------------> The meat, now we read it, this is the part up to the clients (e.g. X-S)
		// 
		
		// This is the usual EMF configured by the BioSD loader (via hibernate.properties)
		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
		
		// This is the facade to get all info stored by the annotator
		// See also the hierarchy rooted at uk.ac.ebi.fg.biosd.annotator.model.FeatureAnnotation 
		AnnotatorAccessor annAccessor = new AnnotatorAccessor ( em );
		
		// You can get all ontologies associated to a PV, either in the original submission, or coming from the 
		// annotator
		List<OntologyEntry> oeanns = annAccessor.getAllOntologyEntries ( pval );
		boolean foundZOOMA = false, foundResolved = false; 
		for ( OntologyEntry oeann: oeanns )
		{
			log.info ( "Ontology Entry found in annotations: URI/ACC: {}, Label: {}", oeann.getAcc (), oeann.getLabel () );
			// We expect ZOOMA to find this
			foundZOOMA |= "http://www.ebi.ac.uk/efo/EFO_0000270".equals ( oeann.getAcc () );

			// And the Bioportal-based resolver to get this from our initial annotation. The latter is replaced
			foundResolved |= "http://purl.bioontology.org/ontology/MESH/D001249".equals ( oeann.getAcc () ); 
		}
		assertTrue ( "ZOOMA didnt't work!",  foundZOOMA );
		assertTrue ( "Bioportal resolution didnt't work!",  foundResolved );

		
		// Again, ontology entry for the unit, no matter if it comes from the original submission, or was annotated
		OntologyEntry uoe = annAccessor.getUnitOntologyEntry ( pvtemp.getUnit () );
		assertNotNull ( "Unit not annotated!", uoe );
		assertEquals  ( "Wrong ontology URI for the unit!", "http://purl.obolibrary.org/obo/UO_0000027", uoe.getAcc () );
		
		// Numerical value possibly extracted by the property textual value
		DataItem di = annAccessor.getExpPropValDataItem ( pvtemp );
		assertTrue ( "Numeric annotation not created!", di != null && di instanceof NumberItem );
		
		NumberItem ni = (NumberItem) di;
		assertTrue ( "Wrong number annotation!", 120 == ni.getValue () );
	}
	
	
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

		// Do this in the unlikely case you first annotated, then use the access API. The annotator leaves around resources
		// that the accessor shouldn't see.
		AnnotatorResources.getInstance ().reset (); 
		
		
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
				ann.getSourceText (), ann.getOntoTermUri (), ObjectUtils.defaultIfNull ( ann.getScore (), 0d ) 
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
		
		// Do this in the unlikely case you first annotated, then use the access API. The annotator leaves around resources
		// that the accessor shouldn't see.
		AnnotatorResources.getInstance ().reset ();
		

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
	
	@After
	public void cleanUp ()
	{
		new Purger ().purge ( new DateTime ().minusMinutes ( 1 ).toDate (), new Date() );
	}	
}
