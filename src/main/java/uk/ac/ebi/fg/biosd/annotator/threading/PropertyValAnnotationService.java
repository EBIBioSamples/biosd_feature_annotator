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
import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotationManager;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.BioSDOntoDiscoveringCache;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.OntoDiscoveryAndAnnotator;
import uk.ac.ebi.fg.biosd.model.expgraph.BioSample;
import uk.ac.ebi.fg.biosd.model.organizational.BioSampleGroup;
import uk.ac.ebi.fg.biosd.model.organizational.MSI;
import uk.ac.ebi.fg.biosd.sampletab.loader.Loader;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AccessibleDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.toplevel.Identifiable;
import uk.ac.ebi.fg.core_model.toplevel.TextAnnotation;
import uk.ac.ebi.utils.threading.BatchService;
import uk.org.lidalia.slf4jext.Level;

/**
 * This is the {@link BatchService multi-thread service} to which {@link PropertyValAnnotationTask}s are submitted. It
 * manages a near-fixed size thread pool, which of size is periodically evaluated for performance and dynamically
 * adjusted.
 *
 * <dl><dt>date</dt><dd>3 Sep 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class PropertyValAnnotationService extends BatchService<PropertyValAnnotationTask>
{
	/** 
	 * This is a system property that allows you to define a limit for the number of threads the annotator can use.
	 * This is very important when running the annotator against the cluster, since the default value of 100
	 * leads us to running out of Oracle connections.
	 */
	public static final String MAX_THREAD_PROP = "uk.ac.ebi.fg.biosd.annotator.maxThreads";
	
	private double randomSelectionQuota = 100.0;
	private Random rndGenerator = new Random ( System.currentTimeMillis () );

	private PropertyValAnnotationManager pvAnnMgr = new PropertyValAnnotationManager ();
	
	/**
	 * Used in queries that picks up those properties not associated neither to ZOOMA-computed terms, nor marked
	 * with {@link OntoDiscoveryAndAnnotator#createEmptyZoomaMappingMarker() 'no-ontology marker'}.
	 *  
	 */
	private static final String PV_CRITERIA = 
		  "pv NOT IN (\n"
		+ "	  SELECT pv1.id FROM ExperimentalPropertyValue pv1 JOIN pv1.ontologyTerms oe JOIN oe.annotations oa WHERE\n"
		+ "     oa.type.name = :oeAnnType )\n"
		+ "AND pv NOT IN (\n"
		+ "   SELECT pv2.id FROM ExperimentalPropertyValue pv2 JOIN pv2.annotations pa WHERE\n"
		+ "     pa.type.name = :pvAnnType )\n";

	
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
				MAX_THREAD_PROP, "100" ) ) 
			);
			this.poolSizeTuner.setMinThreads ( 5 );
			this.poolSizeTuner.setMaxThreadIncr ( this.poolSizeTuner.getMaxThreads () / 4 );
			this.poolSizeTuner.setMinThreadIncr ( 5 );
		}
		
		this.setSubmissionMsgLogLevel ( Level.DEBUG );
	}
	
	/**
	 * This is to submit an {@link PropertyValAnnotationTask annotation task} about an {@link ExperimentalPropertyValue} 
	 * with this {@link Identifiable#getId() id}. If {@link #getRandomSelectionQuota()} is &lt; 100, only the 
	 * corresponding random percentage of calls to this method will actually produce a submission.
	 * 
	 */
	public void submit ( long pvalId )
	{
		if ( randomSelectionQuota < 100.0 && rndGenerator.nextDouble () >= randomSelectionQuota ) return;
		super.submit ( new PropertyValAnnotationTask ( pvalId, this.pvAnnMgr ) );
	}

	/**
	 * Invokes {@link #submit(long)} for the properties listed in this offset and position.
	 * This is mainly used to split the property value set in chunks and pass them to LSF-based invocations.
	 * 
	 * This considers only those properties that haven't neither any ZOOMA-computed term associated, nor a 
	 * {@link OntoDiscoveryAndAnnotator#createEmptyZoomaMappingMarker() 'no-ontology term found marker'}.
	 */
	@SuppressWarnings ( { "unchecked" } )
	public void submit ( Integer offset, Integer limit )
	{
		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
		
		try 
		{
			EntityTransaction tx = em.getTransaction ();
			tx.begin ();

			Query q = em.createQuery ( "SELECT id FROM ExperimentalPropertyValue pv WHERE\n" + PV_CRITERIA );
			TextAnnotation oeMarker = BioSDOntoDiscoveringCache.createZOOMAMarker ( "foo", "foo" );
			TextAnnotation pvMarker = OntoDiscoveryAndAnnotator.createEmptyZoomaMappingMarker ();
			q.setParameter ( "oeAnnType", oeMarker.getType ().getName () );
			q.setParameter ( "pvAnnType", pvMarker.getType ().getName () );
			
			if ( offset != null ) q.setFirstResult ( offset );
			if ( limit != null ) q.setMaxResults ( limit );
				
			for ( Number id: (List<Number>) q.getResultList () )
				submit ( id.longValue () );
			
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
	
	/**
	 * Submits an {@link PropertyValAnnotationTask annotation task} for every property linked to a {@link BioSample sample}
	 * or {@link BioSampleGroup sample group} linked to this submission.
	 * 
	 */
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
	
	/**
	 * Invokes {@link #submitMSI(MSI)}, after accession-based lookup. Exception is thrown if the accession doesn't
	 * exist.
	 */
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

	/**
	 * Invokes {@link #submitMSI(String)}, after having extracted the accession from text content expressed in SampleTab
	 * format. Exceptions are thrown if such content is wrong.
	 */
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

	
	
	/**
	 * Gets the number of properties in the BioSD database. To be used prior to {@link #submit(Integer, Integer)}.
	 * 
	 * This considers only those properties that haven't neither any ZOOMA-computed term associated, nor a 
	 * {@link OntoDiscoveryAndAnnotator#createEmptyZoomaMappingMarker() 'no-ontology term found marker'}.
	 *
	 */
	public int getPropValCount ()
	{
		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
		try
		{
			EntityTransaction tx = em.getTransaction ();
			tx.begin ();

			TextAnnotation oeMarker = BioSDOntoDiscoveringCache.createZOOMAMarker ( "foo", "foo" );
			TextAnnotation pvMarker = OntoDiscoveryAndAnnotator.createEmptyZoomaMappingMarker ();
			
			Number count = (Number) em.createQuery (
				"SELECT COUNT (DISTINCT pv.id) FROM ExperimentalPropertyValue pv WHERE\n" + PV_CRITERIA
			).setParameter ( "oeAnnType", oeMarker.getType ().getName () )
			.setParameter ( "pvAnnType", pvMarker.getType ().getName () )
			.getSingleResult ();
			tx.commit ();
			return count.intValue ();
		}
		finally {
			if ( em.isOpen () ) em.close ();
		}
	}


	/**
	 * Allows you to do a bit of testing by anntoating only a random quota of the target set of 
	 * {@link ExperimentalPropertyValue}s for which you invoke the annotation service.
	 * 
	 */
	public double getRandomSelectionQuota ()
	{
		return randomSelectionQuota;
	}

	public void setRandomSelectionQuota ( double randomSelectionQuota )
	{
		this.randomSelectionQuota = randomSelectionQuota;
	}

}
