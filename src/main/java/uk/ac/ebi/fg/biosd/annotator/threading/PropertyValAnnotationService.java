package uk.ac.ebi.fg.biosd.annotator.threading;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.Query;

import org.apache.commons.lang3.StringUtils;

import uk.ac.ebi.arrayexpress2.magetab.exception.ParseException;
import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotator;
import uk.ac.ebi.fg.biosd.model.expgraph.BioSample;
import uk.ac.ebi.fg.biosd.model.organizational.BioSampleGroup;
import uk.ac.ebi.fg.biosd.model.organizational.MSI;
import uk.ac.ebi.fg.biosd.sampletab.loader.Loader;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AccessibleDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.utils.threading.BatchService;;

/**
 * TODO: Comment me!
 *
 * <dl><dt>date</dt><dd>3 Sep 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class PropertyValAnnotationService extends BatchService<PropertyValAnnotationTask>
{
	private double randomSelectionQuota = 100.0;
	private Random rndGenerator = new Random ( System.currentTimeMillis () );

	private PropertyValAnnotator propertyValAnnotator = new PropertyValAnnotator ();
	
	
	public PropertyValAnnotationService ()
	{
		super ();
		// super ( 1, null ); //DEBUG
		// Sometimes I set it to null for debugging purposes
		if ( this.poolSizeTuner != null ) 
		{
			this.poolSizeTuner.setPeriodMSecs ( (int) 5*60*1000 );
			// TODO: document this
			this.poolSizeTuner.setMaxThreads ( Integer.parseInt ( System.getProperty ( 
				"uk.ac.ebi.fg.biosd.annotator.maxThreads", "100" ) ) 
			);
			this.poolSizeTuner.setMinThreads ( 5 );
			this.poolSizeTuner.setMaxThreadIncr ( this.poolSizeTuner.getMaxThreads () / 4 );
			this.poolSizeTuner.setMinThreadIncr ( 5 );
		}
		
		
	}
	
	public void submit ( long pvalId )
	{
		if ( randomSelectionQuota < 100.0 && rndGenerator.nextDouble () >= randomSelectionQuota ) return;
		super.submit ( new PropertyValAnnotationTask ( pvalId, this.propertyValAnnotator ) );
	}

	@SuppressWarnings ( { "unchecked" } )
	public void submit ( Integer offset, Integer limit )
	{
		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
		
		try 
		{
			EntityTransaction tx = em.getTransaction ();
			tx.begin ();
			Query q = em.createQuery ( "SELECT id FROM ExperimentalPropertyValue pv" );
			if ( offset != null ) q.setFirstResult ( offset );
			if ( limit != null ) q.setMaxResults ( limit );
				
			for ( Number id: (List<Number>) q.getResultList () )
				submit ( id.intValue () );
			
			tx.commit ();
		}
		finally {
			if ( em.isOpen () ) em.close ();
		}
	}

	public void submitAll ()
	{
		submit ( null, null );
	}
	
	public void submitMSI ( MSI msi )
	{
		Set<Long> pvIds = new HashSet<> ();
		log.info ( "Scanning {} sample groups", msi.getSampleGroups ().size () );
		for ( BioSampleGroup sg: msi.getSampleGroups () )
		{
			for ( ExperimentalPropertyValue<?> pv: sg.getPropertyValues () ) pvIds.add ( pv.getId () );
			for ( BioSample smp: sg.getSamples () )
				for ( ExperimentalPropertyValue<?> pv: smp.getPropertyValues () ) pvIds.add ( pv.getId () );
		}

		log.info ( "Scanning {} samples", msi.getSamples ().size () );
		for ( BioSample smp: msi.getSamples () )
			for ( ExperimentalPropertyValue<?> pv: smp.getPropertyValues () ) pvIds.add ( pv.getId () );
		
		log.info ( "Starting annotation tasks for {} property values", pvIds.size () );
		for ( long pvid: pvIds ) submit ( pvid );
	}
	
	
	public void submitMSI ( String msiAcc )
	{
		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
		
		try
		{
			AccessibleDAO<MSI> dao = new AccessibleDAO<> ( MSI.class, em );
			EntityTransaction tx = em.getTransaction ();
			tx.begin ();
			MSI msi = dao.find ( msiAcc );
			if ( msi == null ) throw new RuntimeException ( "Cannot find submission '" + msiAcc + "'" );
			submitMSI ( msi );
			tx.commit ();
		}
		finally {
			if ( em.isOpen () ) em.close ();
		}
	}

	public void submitMSI ( InputStream sampleTabIn )
	{
		try
		{
			Loader loader = new Loader ();
			loader.setSkipSCD ( true );
			MSI msi = loader.fromSampleData ( sampleTabIn );
			if ( msi == null ) throw new ParseException ( "The parser returns a null MSI object" );
			
			String acc = StringUtils.trimToNull ( msi.getAcc () );
			if ( acc == null ) throw new ParseException ( "Null sunmission accession found in the SampleTab file" );
			
			submitMSI ( acc );
		} 
		catch ( ParseException ex )
		{
			throw new RuntimeException ( "Error reading SampleTab file: " + ex.getMessage (), ex );
		}
	}

	
	
	
	public int getPropValCount ()
	{
		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
		try
		{
			EntityTransaction tx = em.getTransaction ();
			tx.begin ();
			Number count = (Number) em.createQuery (
				"SELECT COUNT (DISTINCT pv.id) FROM ExperimentalPropertyValue pv"
			).getSingleResult ();
			tx.commit ();
			return count.intValue ();
		}
		finally {
			if ( em.isOpen () ) em.close ();
		}
	}

	public double getRandomSelectionQuota ()
	{
		return randomSelectionQuota;
	}

	public void setRandomSelectionQuota ( double randomSelectionQuota )
	{
		this.randomSelectionQuota = randomSelectionQuota;
	}

}
