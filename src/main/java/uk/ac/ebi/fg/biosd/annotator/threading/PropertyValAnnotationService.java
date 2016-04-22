package uk.ac.ebi.fg.biosd.annotator.threading;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.Hibernate;

import uk.ac.ebi.arrayexpress2.magetab.exception.ParseException;
import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotationManager;
import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.fg.biosd.annotator.persistence.AnnotatorPersister;
import uk.ac.ebi.fg.biosd.annotator.purge.Purger;
import uk.ac.ebi.fg.biosd.model.expgraph.BioSample;
import uk.ac.ebi.fg.biosd.model.organizational.BioSampleGroup;
import uk.ac.ebi.fg.biosd.model.organizational.MSI;
import uk.ac.ebi.fg.biosd.sampletab.loader.Loader;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.expgraph.properties.Unit;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AccessibleDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.toplevel.Identifiable;
import uk.ac.ebi.onto_discovery.api.OntologyTermDiscoverer;
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
public class PropertyValAnnotationService extends BatchService<AnnotatorTask>
{
	/** 
	 * This is a system property that allows you to define a limit for the number of threads the annotator can use.
	 * This is very important when running the annotator against the cluster, since the default value of 100
	 * leads us to running out of Oracle connections.
	 */
	public static final String MAX_THREAD_PROP = AnnotatorResources.PROP_NAME_PREFIX + "maxThreads";
	
	double randomSelectionQuota = 1.0;
		
	public PropertyValAnnotationService ()
	{
		super ();
		// super ( 1, null ); // DEBUG
		// Sometimes I set it to null for debugging purposes
		if ( this.poolSizeTuner != null ) 
		{
			this.poolSizeTuner.setPeriodMSecs ( (int) 5*60*1000 );
			// TODO: document this
			int maxThreads = Integer.parseInt ( System.getProperty ( MAX_THREAD_PROP, "250" ) );
			int minThreads = Math.min ( maxThreads, Runtime.getRuntime().availableProcessors() );
			this.poolSizeTuner.setMinThreads ( minThreads );
			this.poolSizeTuner.setMaxThreads ( maxThreads	);
			this.poolSizeTuner.setMaxThreadIncr ( Math.max ( 5, this.poolSizeTuner.getMaxThreads () / 4 ) );
			this.poolSizeTuner.setMinThreadIncr ( 5 );
			this.setThreadPoolSize ( minThreads );
		}
		
		this.setSubmissionMsgLogLevel ( Level.DEBUG );
	}
	
	/**
	 * This is to submit an {@link PropertyValAnnotationTask annotation task} about an {@link ExperimentalPropertyValue} 
	 * with this {@link Identifiable#getId() id}. If {@link #getRandomSelectionQuota()} is &lt; 1, only the 
	 * corresponding random percentage of calls to this method will actually produce a submission.
	 * 
	 */
	public void submit ( ExperimentalPropertyValue<ExperimentalPropertyType> pv )
	{
		try
		{
			// We need to force eager mode, we're about to close the session
			Hibernate.initialize ( pv.getType () );
			Hibernate.initialize ( pv.getTermText () );
			Hibernate.initialize ( pv.getOntologyTerms () );
	
			Unit u;
			Hibernate.initialize ( u = pv.getUnit () );
			if ( u != null ) 
			{
				Hibernate.initialize ( u.getOntologyTerms () );
				Hibernate.initialize ( u.getTermText () );
				Hibernate.initialize ( u.getDimension () );
			}
			
			super.submit ( new PropertyValAnnotationTask ( pv ) );
		}
		catch ( Throwable ex ) 
		{			
			log.error ( String.format ( 
				"Error while submitting property %s: %s, ignoring this property", pv, ex.getMessage () ), 
				ex 
			);
			
			// TODO: proper exit code
			lastExitCode = 1;
		}
	}

	/**
	 * Invokes {@link #submit(long)} for the properties listed in this offset and position.
	 * This is mainly used to split the property value set in chunks and pass them to LSF-based invocations.
	 * 
	 */
	public void submit ( Integer offset, Integer limit , boolean purgeFirst)
	{
		if ( offset == null ) offset = 0;
		if ( limit == null ) limit = this.getPropValCount ();
		
		// Half of the CPUs used to scroll the chunks, all the rest used to process single properties
		int chunkSize = (int) Math.ceil ( limit / ( this.poolSizeTuner.getMinThreads () / 2d ) );
		if ( chunkSize == 0 ) chunkSize = limit;

		for ( int ichunk = 0; ichunk < limit; ichunk += chunkSize )
			super.submit ( new PvChunkSubmissionTask ( this, ichunk + offset, chunkSize, purgeFirst ) );
	}

	public void submitAll ()
	{
		submit ( null, null , false);
	}
	
	/**
	 * Submits an {@link PropertyValAnnotationTask annotation task} for every property linked to a {@link BioSample sample}
	 * or {@link BioSampleGroup sample group} linked to this submission.
	 * 
	 */
	@SuppressWarnings ( "unchecked" )
	public void submitMSI (MSI msi )
	{
		log.info ( "Submission '{}', annotating {} sample groups", msi.getAcc (), msi.getSampleGroups ().size () );

		Purger purger = new Purger();

		for (BioSampleGroup sg : msi.getSampleGroups()) {
			for (BioSample smp : sg.getSamples())
				for (ExperimentalPropertyValue<ExperimentalPropertyType> pv : smp.getPropertyValues()) {
					purger.purgePVAnnotations(pv);
					purger.purgeResolvedOntTerms(pv);
					submit(pv);
				}
		}

		log.info("Submission '{}', annotating {} samples", msi.getAcc(), msi.getSamples().size());
		for (BioSample smp : msi.getSamples())
			for (ExperimentalPropertyValue<ExperimentalPropertyType> pv : smp.getPropertyValues()) {
				purger.purgePVAnnotations(pv);
				purger.purgeResolvedOntTerms(pv);
				submit(pv);
			}
	}

	public ArrayList getPropertyValuesOfMSI(MSI msi){
		ArrayList<ExperimentalPropertyValue<ExperimentalPropertyType>> propertyValues = new ArrayList<>();

		for (BioSampleGroup sg : msi.getSampleGroups()) {
			for (BioSample smp : sg.getSamples())
				for (ExperimentalPropertyValue<ExperimentalPropertyType> pv : smp.getPropertyValues()) {
					propertyValues.add(pv);
				}
		}

		log.info("Submission '{}', annotating {} samples", msi.getAcc(), msi.getSamples().size());
		for (BioSample smp : msi.getSamples())
			for (ExperimentalPropertyValue<ExperimentalPropertyType> pv : smp.getPropertyValues()) {
				propertyValues.add(pv);
			}

		return propertyValues;
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
	 * It adjusts the real total by multiplying it by {@link #getRandomSelectionQuota()}, if this is &lt; 1.
	 */
	public int getPropValCount ()
	{
		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
		
		try
		{
			Number count = (Number) em.createNativeQuery (
				"SELECT COUNT ( pv.id ) FROM exp_prop_val pv"
			).getSingleResult ();
			int result = count.intValue ();
			
			if ( this.randomSelectionQuota < 1d )
				result = (int) Math.round ( result * this.randomSelectionQuota );
			
			return result;
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
	 * Waits for all the tasks to finish and then {@link #persist()}s. 
	 */
	@Override
	public void waitAllFinished ()
	{
		super.waitAllFinished ();
		this.persist ();
	}
	
	
	/**
	 * Saves {@link AnnotatorResources#getStore() gathered annotations}, used by {@link #waitAllFinished()}.
	 */
	private void persist ()
	{
		new AnnotatorPersister ().persist ();
		
		// Just in case it's needed by JUnit tests
		AnnotatorResources.getInstance ().reset ();
		
	} // persist()

}
