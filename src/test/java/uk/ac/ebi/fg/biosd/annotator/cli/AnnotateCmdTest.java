package uk.ac.ebi.fg.biosd.annotator.cli;

import static junit.framework.Assert.assertTrue;

import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.joda.time.DateTime;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.model.expgraph.BioSample;
import uk.ac.ebi.fg.biosd.model.organizational.MSI;
import uk.ac.ebi.fg.biosd.sampletab.loader.Loader;
import uk.ac.ebi.fg.biosd.sampletab.persistence.Persister;
import uk.ac.ebi.fg.biosd.sampletab.persistence.Unloader;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AccessibleDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.toplevel.Annotation;

/**
 * TODO: Comment me!
 *
 * <dl><dt>date</dt><dd>14 Oct 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class AnnotateCmdTest
{
	private Logger log = LoggerFactory.getLogger ( this.getClass () );

	static {
		System.setProperty ( AnnotateCmd.NO_EXIT_PROP, "true" );
	}
	
	
	@Test
	@SuppressWarnings ( "unchecked" )
	public void testSubmitMsi () throws Exception
	{
    URL sampleTabUrl = getClass().getClassLoader().getResource( "GAE-MTAB-27_truncated.sampletab.csv" );

		Loader loader = new Loader ();
		MSI msi = loader.fromSampleData ( sampleTabUrl );
		Persister persister = new Persister ();
		persister.persist ( msi );
		
		//AnnotateCmd.main ( "--submission", msi.getAcc () );
		//AnnotateCmd.main ( "--offset", "0", "--limit", "10" );
		AnnotateCmd.main ( "--random-quota", "10.0" );

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
		
		
		// Clean-up. TODO: use proper facilities.
		DateTime timeThreeshold = new DateTime ().minusMinutes ( 1 );
		for ( OntologyEntry oe: (List<OntologyEntry>) em.createQuery ( "SELECT DISTINCT oe FROM OntologyEntry oe" ).getResultList () )
		{
			log.info ( "Removing annotations for {}", oe );
			EntityTransaction tx = em.getTransaction ();
			tx.begin ();
			Set<Annotation> anns = new HashSet<Annotation> ( oe.getAnnotations () );
			oe.getAnnotations ().clear ();
			for ( Annotation ann: anns )
			{
				if ( new DateTime ( ann.getTimestamp () ).isBefore ( timeThreeshold ) ) continue; 
				log.info ( "Removing {}", ann );
				em.remove ( ann );
			}
			tx.commit ();
		}
		
		//if ( em.isOpen () ) em.close ();
		
		Unloader unloader = new Unloader ();
		unloader.setDoPurge ( true );
		unloader.unload ( msi );

		assertTrue ( "No annotation found!", hasFoundAnn );
	}
}
