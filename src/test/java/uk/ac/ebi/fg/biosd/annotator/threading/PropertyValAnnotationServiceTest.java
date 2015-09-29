package uk.ac.ebi.fg.biosd.annotator.threading;

import static junit.framework.Assert.assertTrue;

import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Query;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.fg.biosd.annotator.model.FeatureAnnotation;
import uk.ac.ebi.fg.biosd.annotator.model.NumberItem;
import uk.ac.ebi.fg.biosd.annotator.model.ResolvedOntoTermAnnotation;
import uk.ac.ebi.fg.biosd.annotator.persistence.dao.ExpPropValAnnotationDAO;
import uk.ac.ebi.fg.biosd.annotator.purge.Purger;
import uk.ac.ebi.fg.biosd.annotator.test.AnnotatorResourcesResetRule;
import uk.ac.ebi.fg.biosd.model.expgraph.BioSample;
import uk.ac.ebi.fg.biosd.model.organizational.MSI;
import uk.ac.ebi.fg.biosd.sampletab.loader.Loader;
import uk.ac.ebi.fg.biosd.sampletab.persistence.Persister;
import uk.ac.ebi.fg.biosd.sampletab.persistence.Unloader;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AccessibleDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.org.lidalia.slf4jext.Level;

/**
 * Tests the {@link PropertyValAnnotationService} and how things work in multi-thread mode.
 *
 * <dl><dt>date</dt><dd>3 Sep 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class PropertyValAnnotationServiceTest
{
	private Logger log = LoggerFactory.getLogger ( this.getClass () );

	@Rule
	public TestRule resResetRule = new AnnotatorResourcesResetRule ();

	/**
	 * Uses the {@link Purger} to remove the ZOOMA-related objects created in these tests.
	 */
	@After
	public void cleanUp ()
	{
		new Purger ().purge ( new DateTime ().minusMinutes ( 5 ).toDate (), new Date() );
	}

	/**
	 * Basic test against a couple of properties.
	 */
	@Test
	@SuppressWarnings ( { "unchecked" } )
	public void testService ()
	{
		// Test against these.
		//
		List<ExperimentalPropertyValue<ExperimentalPropertyType>> pvs = new ArrayList<> ();
		pvs.add ( new ExperimentalPropertyValue<> ( 
			"homo sapiens", new ExperimentalPropertyType ( "specie" ) 
		));
		pvs.add ( new ExperimentalPropertyValue<> ( 
			"mus musculus", new ExperimentalPropertyType ( "organism" ) 
		));
		pvs.add ( new ExperimentalPropertyValue<> ( 
			"123", new ExperimentalPropertyType ( "bla bla bla" ) 
		));	
		pvs.add ( new ExperimentalPropertyValue<> ( 
			pvs.get ( 0 ).getTermText (), pvs.get ( 0 ). getType ()
		));
		
		// Save them
		// 
		EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
		EntityManager em = emf.createEntityManager ();
		
		EntityTransaction tx = em.getTransaction ();
		tx.begin ();
		for ( ExperimentalPropertyValue<ExperimentalPropertyType> pv: pvs ) em.persist ( pv );
		tx.commit ();
		em.close ();
		
		// Annotate them with the multi-thread service
		//
		PropertyValAnnotationService service = new PropertyValAnnotationService ();
		service.setSubmissionMsgLogLevel ( Level.INFO );
		service.submitAll ();
		service.waitAllFinished ();

		// Verify
		// TODO: replace with the interfaces to be developed.
		//
		em = emf.createEntityManager ();
		ExpPropValAnnotationDAO pvannDao = new ExpPropValAnnotationDAO ( em );
		
		for ( int i = 0; i <= 1; i++ )
		{
			ExperimentalPropertyValue<ExperimentalPropertyType> pv = pvs.get ( i );
			List<ExpPropValAnnotation> pvanns = pvannDao.findByExpPropVal ( pv );

			assertTrue ( String.format ( "No annotations saved for %s!", pv.getTermText () ), pvanns.size () > 0 );
			
			log.info ( "--------------------------- Saved annotations for '{}' --------------------------", pv.getTermText () );
			for ( ExpPropValAnnotation pvann: pvanns ) {
				log.info ( pvann.toString () );
			}
		}
		
		Query q = em.createQuery ( "FROM NumberItem WHERE value = 123" );
		List<NumberItem> nums = q.getResultList ();
		assertTrue ( "Number annotation not saved!", nums.size () > 0 );
		log.info ( "------------------------ Number annotation saved: {} ---------------------", nums.get ( 0 ) );

		em.close ();
	}
	
	/**
	 * Tests against a real-world submission.
	 */
	@Test
	@SuppressWarnings ( "unchecked" )
	public void testSubmitMsi () throws Exception
	{
		// The test case is stored in the Maven project, load it
		//
    URL sampleTabUrl = getClass().getClassLoader().getResource( "GAE-MTAB-27_truncated.sampletab.csv" );

		Loader loader = new Loader ();
		MSI msi = loader.fromSampleData ( sampleTabUrl );
		Persister persister = new Persister ();
		persister.persist ( msi );
		
		// Use the multi-thread service
		//
		PropertyValAnnotationService service = new PropertyValAnnotationService ();
		service.setSubmissionMsgLogLevel ( Level.INFO );
		service.submitMSI ( sampleTabUrl.openStream () );
		service.waitAllFinished ();

		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
		AccessibleDAO<MSI> dao = new AccessibleDAO<> ( MSI.class, em );
		
		msi = dao.find ( msi.getAcc () );
		
		log.info ( "----------- Showing Computed Annotations --------" );
		boolean hasFoundAnn = false;
		
		for ( BioSample smp: msi.getSamples () )
			for ( ExperimentalPropertyValue<?> pv: smp.getPropertyValues () )
			{
				List<FeatureAnnotation> anns = new LinkedList<> ();
				anns.addAll ( em.createQuery ( "FROM ExpPropValAnnotation ann WHERE sourceText = :pvTxt" )
				  .setParameter ( "pvTxt", ExpPropValAnnotation.getPvalText ( pv ) )
				  .getResultList ()
				);
				anns.addAll ( em.createQuery ( "FROM DataItem ann WHERE sourceText = :pvTxt" )
				 .setParameter ( "pvTxt", pv.getTermText () )
				 .getResultList () 
				);
				for ( OntologyEntry oe: pv.getOntologyTerms () )
					anns.addAll ( em.createQuery ( "FROM ResolvedOntoTermAnnotation ann WHERE sourceText = :pvTxt" )
					  .setParameter ( "pvTxt", ResolvedOntoTermAnnotation.getOntoEntryText ( oe ) )
					  .getResultList () 
				);
				
				for ( FeatureAnnotation fa: anns )
				{
					log.info ( "Value: {}, annotation: {}", pv.getTermText (), fa.toString () );
					hasFoundAnn = true;
				}
		}
		
		em.close ();
		
		// Remove the submission
		//
		Unloader unloader = new Unloader ();
		unloader.setDoPurge ( true );
		unloader.unload ( msi );

		// Report about verified annotations.
		assertTrue ( "No annotation found!", hasFoundAnn );
	}
}
