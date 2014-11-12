package uk.ac.ebi.fg.biosd.annotator.threading;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import junit.framework.Assert;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.ontodiscover.BioSDOntoDiscoveringCache;
import uk.ac.ebi.fg.biosd.annotator.purge.Purger;
import uk.ac.ebi.fg.biosd.model.expgraph.BioSample;
import uk.ac.ebi.fg.biosd.model.organizational.MSI;
import uk.ac.ebi.fg.biosd.sampletab.loader.Loader;
import uk.ac.ebi.fg.biosd.sampletab.persistence.Persister;
import uk.ac.ebi.fg.biosd.sampletab.persistence.Unloader;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.terms.OntologyEntryDAO;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AccessibleDAO;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AnnotatableDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.toplevel.Annotation;

/**
 * TODO: Comment me!
 *
 * <dl><dt>date</dt><dd>3 Sep 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class PropertyValAnnotationServiceTest
{
	private Logger log = LoggerFactory.getLogger ( this.getClass () );

	@After
	public void cleanUp ()
	{
		new Purger ().purge ( new DateTime ().minusMinutes ( 1 ).toDate (), new Date() );
	}

	
	@Test
	@SuppressWarnings ( "rawtypes" )
	public void testService ()
	{
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
		
		
		EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
		EntityManager em = emf.createEntityManager ();
		
		EntityTransaction tx = em.getTransaction ();
		tx.begin ();
		for ( ExperimentalPropertyValue<ExperimentalPropertyType> pv: pvs ) em.persist ( pv );
		tx.commit ();

		//if ( em.isOpen () ) em.close ();
		
		PropertyValAnnotationService service = new PropertyValAnnotationService ();
		service.submitAll ();
		service.waitAllFinished ();

		
		em = emf.createEntityManager ();
		AnnotatableDAO<ExperimentalPropertyValue> pvdao = new AnnotatableDAO<> ( ExperimentalPropertyValue.class, em );

		Set<Long> foundOeIds = new HashSet<> ();
		for ( ExperimentalPropertyValue<ExperimentalPropertyType> pv: pvs )
		{
			ExperimentalPropertyValue<?> pvdb = pvdao.find ( pv.getId () );
			Assert.assertNotNull ( "Property: " + pv + " not saved!", pvdb );
			
			Set<OntologyEntry> oes = pvdb.getOntologyTerms ();
			log.info ( "Stored Property Value: {}", pvdb );
			
			for ( OntologyEntry oe: oes ) foundOeIds.add ( oe.getId () );
		}
		
		assertEquals ( "Couldn't find expected ontology entries!", 2, foundOeIds.size () );
	
		
		// Clean-up
		tx = em.getTransaction ();
		tx.begin ();		
		for ( ExperimentalPropertyValue<ExperimentalPropertyType> pv: pvs )
		{
			ExperimentalPropertyValue<?> pvdb = pvdao.find ( pv.getId () );
			for ( OntologyEntry oe: pvdb.getOntologyTerms () )
			{
				for ( Annotation ann: oe.getAnnotations () )
					em.remove ( ann );
				em.remove ( oe );
			}
			em.remove ( pvdb );
		}
		
		OntologyEntryDAO<OntologyEntry> oedao = new OntologyEntryDAO<> ( OntologyEntry.class, em );
		OntologyEntry nullOe = oedao.find ( BioSDOntoDiscoveringCache.NULL_TERM_URI, null, null, null );
		for ( Annotation ann: nullOe.getAnnotations () ) em.remove ( ann );
		em.remove ( nullOe );
		
		tx.commit ();
		
	}
	
	
	//@Test
	public void testSubmitMsi () throws Exception
	{
    URL sampleTabUrl = getClass().getClassLoader().getResource( "GAE-MTAB-27_truncated.sampletab.csv" );

		Loader loader = new Loader ();
		MSI msi = loader.fromSampleData ( sampleTabUrl );
		Persister persister = new Persister ();
		persister.persist ( msi );
		
		PropertyValAnnotationService service = new PropertyValAnnotationService ();
		service.submitMSI ( sampleTabUrl.openStream () );
		service.waitAllFinished ();
		
		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
		AccessibleDAO<MSI> dao = new AccessibleDAO<> ( MSI.class, em );
		
		msi = dao.find ( msi.getAcc () );
		
		log.info ( "Showing Computed Annotations" );
		boolean hasFoundAnn = false;
		for ( BioSample smp: msi.getSamples () )
			for ( ExperimentalPropertyValue<?> pv: smp.getPropertyValues () )
				for ( OntologyEntry oe: pv.getOntologyTerms () )
					for ( Annotation ann: oe.getAnnotations () )
					{
						log.info ( String.format ( 
							"Annotated Property: %s/%s[#%d], %s[#%d], %f", 
							pv.getTermText (), pv.getType ().getTermText (), pv.getId (), 
							oe.getAcc (), oe.getId (), 
							ann.getScore ()
						));
						
						hasFoundAnn = true;
		}
		
		
		Unloader unloader = new Unloader ();
		unloader.setDoPurge ( true );
		unloader.unload ( msi );

		assertTrue ( "No annotation found!", hasFoundAnn );
	}
}
