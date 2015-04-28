package uk.ac.ebi.fg.biosd.annotator.threading;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import javax.persistence.Query;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;

import uk.ac.ebi.arrayexpress2.magetab.exception.ParseException;
import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.BioSDOntoDiscoveringCache;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.OntoDiscoveryAndAnnotator;
import uk.ac.ebi.fg.biosd.annotator.persistence.AnnotatorPersister;
import uk.ac.ebi.fg.biosd.model.expgraph.BioSample;
import uk.ac.ebi.fg.biosd.model.organizational.BioSampleGroup;
import uk.ac.ebi.fg.biosd.model.organizational.MSI;
import uk.ac.ebi.fg.biosd.sampletab.loader.Loader;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AccessibleDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.toplevel.Identifiable;
import uk.ac.ebi.fg.core_model.toplevel.TextAnnotation;
import uk.ac.ebi.utils.memory.MemoryUtils;
import uk.ac.ebi.utils.threading.BatchService;
import uk.ac.ebi.utils.time.XStopWatch;
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

	private XStopWatch timer = new XStopWatch (); 
	
	private Runnable memFlushAction = new Runnable() 
	{
		@Override
		public void run () {
			PropertyValAnnotationService.this.waitAllFinished ();
		}
	};

	
	/**
	 * Used in queries that picks up those properties not associated neither to ZOOMA-computed terms, nor marked
	 * with {@link OntoDiscoveryAndAnnotator#createEmptyZoomaMappingMarker() 'no-ontology marker'}.
	 *  
	 */
	private static final String PV_CRITERIA =
		   "pv.id NOT IN \n"
		 + "(    \n"
		 + "	SELECT\n"
		 + "	    pv1.id\n"
		 + "	FROM\n"
		 + "	    exp_prop_val pv1 INNER JOIN exp_prop_val_annotation pv2ann1\n"
		 + "	        ON pv1.id = pv2ann1.owner_id INNER JOIN annotation ann1\n"
		 + "	        ON pv2ann1.annotation_id = ann1.id ,\n"
		 + "	    annotation_type atype1\n"
		 + "	WHERE\n"
		 + "	    ann1.type_id = atype1.id\n"
		 + "	    AND atype1.name = :pvAnnType\n"
		 + "	UNION    \n"
		 + "	SELECT\n"
		 + "	    pv2.id\n"
		 + "	FROM\n"
		 + "	    exp_prop_val pv2 INNER JOIN exp_prop_val_onto_entry pv2oe2\n"
		 + "	        ON pv2.id = pv2oe2.owner_id INNER JOIN onto_entry oe2\n"
		 + "	        ON pv2oe2.oe_id = oe2.id INNER JOIN onto_entry_annotation oe2ann2\n"
		 + "	        ON oe2.id = oe2ann2.owner_id INNER JOIN annotation ann2\n"
		 + "	        ON oe2ann2.annotation_id = ann2.id ,\n"
		 + "	    annotation_type atype2\n"
		 + "	WHERE\n"
		 + "	    ann2.type_id = atype2.id\n"
		 + "	    AND atype2.name = :oeAnnType\n"
		 + ")\n";			
		 

	
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
				MAX_THREAD_PROP, "250" ) ) 
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
		
		if ( timer.isStopped () ) timer.start ();
				
		// This will flush the collected annotations to the DB when the memory is too full, or when enough time 
		// has passed We add the time criterion, cause we don't want to waste too much if the storage operation fails.
		//
		if ( !MemoryUtils.checkMemory ( this.memFlushAction, 0.25 ) && timer.getTime () > 1000 * 3600 * 3 )
			this.memFlushAction.run ();
		
		super.submit ( new PropertyValAnnotationTask ( pvalId ) );
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
			Query q = em.createNativeQuery ( "SELECT id FROM exp_prop_val pv WHERE\n" + PV_CRITERIA );
			TextAnnotation oeMarker = BioSDOntoDiscoveringCache.createZOOMAMarker ( "foo", "foo" );
			TextAnnotation pvMarker = OntoDiscoveryAndAnnotator.createEmptyZoomaMappingMarker ();
			q.setParameter ( "oeAnnType", oeMarker.getType ().getName () );
			q.setParameter ( "pvAnnType", pvMarker.getType ().getName () );
			
			if ( offset != null ) q.setFirstResult ( offset );
			if ( limit != null ) q.setMaxResults ( limit );
			
			// In the case of bloody Oracle, the damn Hibernate adds ROWID to the projection, which doesn't happen with
			// H2 (i.e., decent SQL syntax that supports LIMIT/OFFSET)
			// We have to workaround the bastard the way below
			//
			List<Object> ids = q.getResultList ();
			for ( Object ido: ids ) 
			{
				long id = ido instanceof Number 
					? ((Number) ido).longValue ()
					: ( (Number) ( (Object[]) ido )[ 0 ] ).longValue (); // see above why
				submit ( id );
			}
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
			MSI msi = dao.find ( msiAcc );
			if ( msi == null ) throw new RuntimeException ( "Cannot find submission '" + msiAcc + "'" );
			submitMSI ( msi );
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
			TextAnnotation oeMarker = BioSDOntoDiscoveringCache.createZOOMAMarker ( "foo", "foo" );
			TextAnnotation pvMarker = OntoDiscoveryAndAnnotator.createEmptyZoomaMappingMarker ();
			
			Number count = (Number) em.createNativeQuery (
				"SELECT COUNT ( pv.id ) FROM exp_prop_val pv WHERE\n" + PV_CRITERIA
			).setParameter ( "oeAnnType", oeMarker.getType ().getName () )
			.setParameter ( "pvAnnType", pvMarker.getType ().getName () )
			.getSingleResult ();
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

	/**
	 * First waits that all the tasks are finished, then flushes all in-memory changes to the database. Finally
	 * cleans up the memory and resets {@link #timer}.
	 * 
	 */
	@Override
	public void waitAllFinished ()
	{
		this.timer.reset ();
		super.waitAllFinished ();
		
		// Try more times, in the attempt to face concurrency issues.
		RuntimeException theEx = null;
		for ( int attempts = 5; attempts > 0; attempts-- )
		{
			theEx = null;
			try {
				new AnnotatorPersister ().persist ();
				break;
			}
			catch ( PersistenceException ex )
			{
				log.trace ( String.format ( 
					"Database error: '%s', probably due to concurrency issues, retrying %d more time(s)", ex.getMessage (), attempts ), 
					ex
				);
				theEx = ex;
				try {
					Thread.sleep ( RandomUtils.nextLong ( 50, 1000 ) );
				}
				catch ( InterruptedException ex1 ) {
					throw new RuntimeException ( "Internal error: " + ex1.getMessage (), ex1 );
				}
			}
		}
		if ( theEx != null ) throw new PersistenceException ( 
			"Couldn't fetch data from the BioSD database, giving up after 5 attempts, likely due to: " + theEx.getMessage (),
			theEx 
		);
		
		AnnotatorResources.reset ();
	}
}
