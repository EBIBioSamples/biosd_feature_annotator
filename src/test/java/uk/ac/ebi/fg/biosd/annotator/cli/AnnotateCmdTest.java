package uk.ac.ebi.fg.biosd.annotator.cli;

import static junit.framework.Assert.assertTrue;

import java.io.StringWriter;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.apache.commons.io.output.WriterOutputStream;
import org.joda.time.DateTime;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.fg.biosd.annotator.model.FeatureAnnotation;
import uk.ac.ebi.fg.biosd.annotator.model.ResolvedOntoTermAnnotation;
import uk.ac.ebi.fg.biosd.annotator.test.AnnotatorResourcesResetRule;
import uk.ac.ebi.fg.biosd.model.expgraph.BioSample;
import uk.ac.ebi.fg.biosd.model.organizational.MSI;
import uk.ac.ebi.fg.biosd.sampletab.loader.Loader;
import uk.ac.ebi.fg.biosd.sampletab.persistence.Persister;
import uk.ac.ebi.fg.biosd.sampletab.persistence.Unloader;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AccessibleDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.utils.regex.RegEx;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.filter.LevelFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;

/**
 * Tests the syntax and invocation of {@link AnnotateCmd}.
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
	
	@Rule
	public TestRule resResetRule = new AnnotatorResourcesResetRule ();

	
	@Test
	public void testSubmitMsiCmd () throws Exception
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
			{
				List<FeatureAnnotation> anns = new LinkedList<> ();
				anns.addAll ( 
					em.createQuery ( "FROM ExpPropValAnnotation ann WHERE sourceText = :pvTxt", FeatureAnnotation.class )
				  	.setParameter ( "pvTxt", ExpPropValAnnotation.getPvalText ( pv ) )
				  	.getResultList ()
				);
				anns.addAll ( em.createQuery ( "FROM DataItem ann WHERE sourceText = :pvTxt", FeatureAnnotation.class )
				 .setParameter ( "pvTxt", pv.getTermText () )
				 .getResultList () 
				);
				for ( OntologyEntry oe: pv.getOntologyTerms () )
					anns.addAll ( 
						em.createQuery ( "FROM ResolvedOntoTermAnnotation ann WHERE sourceText = :pvTxt", FeatureAnnotation.class )
					  	.setParameter ( "pvTxt", ResolvedOntoTermAnnotation.getOntoEntryText ( oe ) )
					  	.getResultList () 
				);
				
				for ( FeatureAnnotation fa: anns )
				{
					log.info ( "Value: {}, annotation: {}", pv.getTermText (), fa.toString () );
					hasFoundAnn = true;
				}
			}
		
		// Clean-up, let's fix things to allow '--purge' to behave correctly
		EntityTransaction tx = em.getTransaction ();
		tx.begin ();
		for ( String entityName: new String[] { "ExpPropValAnnotation", "DataItem", "ResolvedOntoTermAnnotation" } )
			em.createQuery ( "UPDATE " + entityName + " SET timestamp = :tsNew WHERE timestamp >= :tsOld" )
				.setParameter ( "tsOld", new DateTime().minusMinutes ( 1 ).toDate () )
				.setParameter ( "tsNew", new DateTime ().minusYears ( 6 ).toDate () )
				.executeUpdate ();
		tx.commit ();
		em.close ();
		
		// Catch the logger output, to verify results
		//
    LoggerContext logCtx = (LoggerContext) LoggerFactory.getILoggerFactory();

    PatternLayoutEncoder encoder = new PatternLayoutEncoder ();
		encoder.setPattern ( "%message%n" );
		encoder.setContext ( logCtx );
		encoder.start ();
		
		StringWriter sw = new StringWriter ();
		
		LevelFilter filter = new LevelFilter ();
		filter.setLevel ( Level.INFO );
		filter.start ();
		
		OutputStreamAppender<ILoggingEvent> appender = new OutputStreamAppender<> ();
		appender.setEncoder ( encoder );
		appender.setOutputStream ( new WriterOutputStream ( sw ) );
		appender.addFilter ( filter );
		appender.setContext ( logCtx );
		appender.start ();
		
		ch.qos.logback.classic.Logger rootLogger = logCtx.getLogger ( AnnotateCmd.class );
    rootLogger.addAppender ( appender );

    // Now go with the call
		AnnotateCmd.main ( "--purge", "" + 365 * 5 );
		
		// Remove the submission
		Unloader unloader = new Unloader ();
		unloader.setDoPurge ( true );
		unloader.unload ( msi );
		
		// Check results
		String purgeOut = sw.toString ();
		System.out.println ( "\n\n-------- OUT -------\n" + purgeOut + "----- -------- -----\n\n\n" );
		RegEx nelemRe = new RegEx ( 
			".*older annotator entries purged, ([0-9]+) item\\(s\\) removed.*",
			Pattern.MULTILINE | Pattern.DOTALL 
		);
		int nelems = Integer.parseInt ( nelemRe.groups ( purgeOut ) [ 1 ] );
		assertTrue ( "--purge command didn't work!", nelems > 0 );


		// Report about initial check.
		assertTrue ( "No annotation found!", hasFoundAnn );
		
	}
}
