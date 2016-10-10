package uk.ac.ebi.fg.biosd.annotator.cli;

import static java.lang.System.out;

import java.io.*;
import java.util.ArrayList;
import java.util.Scanner;

import javax.persistence.EntityManagerFactory;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.persistence.AnnotatorAccessor;
import uk.ac.ebi.fg.biosd.annotator.persistence.AnnotatorExporter;
import uk.ac.ebi.fg.biosd.annotator.persistence.AnnotatorPersister;
import uk.ac.ebi.fg.biosd.annotator.purge.Purger;
import uk.ac.ebi.fg.biosd.annotator.threading.PropertyValAnnotationService;
import uk.ac.ebi.fg.biosd.model.organizational.MSI;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.resources.Resources;

/**
 * The annotation command. All the work is invoked through an .sh script in the final binary, which in turn invokes this.
 *
 * <dl><dt>date</dt><dd>13 Oct 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class AnnotateCmd
{
	/**
	 * If you set this to true, main() will not invoke {@link System#exit(int)}. This is useful in unit tests.
	 */
	public static final String NO_EXIT_PROP = "uk.ac.ebi.debug.no_jvm_exit"; 
			
	private static int exitCode = 0;
	
	private static Logger log = LoggerFactory.getLogger ( AnnotateCmd.class );

	private static PropertyValAnnotationService annService = null;

	private static AnnotatorExporter annotatorExporter = null;
	
	public static void main ( String... args )
	{
		try
		{
			// Add a termination handler, unless we're doing JUnit testing (invoked explicitly in such a case)
			if ( !"true".equals ( System.getProperty ( NO_EXIT_PROP ) ) )
				Runtime.getRuntime ().addShutdownHook ( new Thread ( new Runnable() 
				{
					@Override
					public void run () {
						terminationHandler ();
					}
				}));
			
			CommandLineParser clparser = new GnuParser ();
			CommandLine cli = clparser.parse ( getOptions(), args );
			
			// Check argument consistency.
			int xopts = 0;
			if ( cli.hasOption ( "help" ) )
				xopts = 3;
			else 
			{
				if ( cli.hasOption ( "submission" ) || cli.hasOption ( "sampletab" ) ) xopts++;
				if ( cli.hasOption ( "offset" ) || cli.hasOption ( "limit" ) ) xopts++;
				if ( cli.hasOption ( "purge" ) ) xopts++;
				
				if ( cli.hasOption ( "property-count" ) || cli.hasOption ( "unlock" ) )
				{
					// random-quota is incompatible with the above options, while it is with the others
					if ( cli.hasOption ( "random-quota" ) )
						xopts = 2;
					else
						xopts++;
				}
			}
			
			if ( xopts > 2 )
			{
				printUsage ();
				return;
			}
			
						
			Double rndQuota = Double.valueOf ( cli.getOptionValue ( "random-quota", "100.0" ) );
			rndQuota = rndQuota < 100.0 && rndQuota >= 0.0 ? rndQuota / 100d : null;
		  
			
			// Clean-up older annotations
		  if ( cli.hasOption ( "purge" ) )
		  {
		  	int age = Integer.parseInt ( cli.getOptionValue ( "purge", "90" ) );
		  	Purger purger = new Purger ();
		  	if ( rndQuota != null ) purger.setDeletionRate ( rndQuota );
		  	
		  	log.info ( StringUtils.center ( String.format ( 
		  		" Removing annotator entities older than %d day(s), please wait... ", age 
		  	), 90, "-" ));
		  	int nitems = purger.purgeOlderThan ( age );
		  	log.info ( "older annotator entries purged, {} item(s) removed", nitems ); 
		  	return;
		  }
		  
		  // Reset locks
		  if ( cli.hasOption ( "unlock" ) )
		  {
				log.info ( StringUtils.center ( " Forcibly removing annotator lock flags from the database ", 90, '-' ));
		  	new AnnotatorPersister ().forceUnlock ();
		  	log.info ( "done" );
		  	return;
		  }
		  
		  
			
		  	annService = new PropertyValAnnotationService ();

			annotatorExporter = new AnnotatorExporter();

			// Count experimental property values, invoked by the cluster-based command
		  if ( cli.hasOption ( "property-count" ) )
		  {
		  	out.println ( annService.getPropValCount () );
				log.info ( "all went fine!" );
				annService = null; // Skip the saving job.
		  	return;
		  }

			//Purge before running annotator
			Boolean purgeFirst = cli.hasOption("first-purge");

		  // This is the real annotation job
		  //

			// Per submission accession stats
			String msiAccsStats = cli.getOptionValue ( "submissionAccessions" );
			if ( msiAccsStats != null )
			{
				exitCode = 127;

				String dirLocation = cli.getOptionValue("dirLocation");
				if (dirLocation == null){
					throw new FileNotFoundException("Need to set directory location, (i.e. path/directory) for the stats to be stored in");
				}


				Scanner scanner = new Scanner(new File(msiAccsStats));
				String line;

				try {
					while (scanner.hasNextLine()) {
						line = scanner.nextLine();
						//property values for this sumbission accession
						if (cli.hasOption("printAnnotations")) {
							annotatorExporter.printAllOntologyAnnotationsForMSI(annService, line, dirLocation);
						}

						if (cli.hasOption("compare")){
							annotatorExporter.compareDiscoveredAndResolvedForMSI(annService, line, dirLocation);
						}

						if (!cli.hasOption("printAnnotations") && !cli.hasOption("compare")){
							throw new RuntimeException("Need to specify if you want to print all annotations and/or compare discovered with resolved annotations");
						}

						//annotatorExporter.compareDiscoveredAndResolvedForMSI(line, propertyValues, dirLocation);
					}
				} catch (IOException e) {
					throw new IOException("Problem while writing in file");
				}
				finally {
					scanner.close();
				}

				System.exit(exitCode);
			}


			// Per submission accession invocation
			String msiAccs = cli.getOptionValue ( "submission" );
			if ( msiAccs != null )
			{
				try {
					Scanner scanner = new Scanner(new File(msiAccs));
					String line;

					while (scanner.hasNextLine()) {
						line = scanner.nextLine();
						//annService.submit ( null, 1, line );

						annService.submitMSI(line);
					}

					scanner.close();

				} catch (FileNotFoundException e){
					log.info("========== File not found, checking for directly submitted accessions ==========");
					//not a file but maybe just accessions
					String msiAccsesions[] = cli.getOptionValues ( "submission" );
					for ( String msiAcc: msiAccsesions ) {
						annService.submitMSI(msiAcc);
					}
					annService.submitMSI(msiAccs);
				}


			}

			// Per submission file invocation
			String sampleTabs[] = cli.getOptionValues ( "sampletab" );
			if ( sampleTabs != null )
			{
				for ( String sampleTab: sampleTabs )
					annService.submitMSI ( new FileInputStream ( new File ( sampleTab ) ) );
			}
			
			// Invocation over all properties, considering a given window. This is used by the LSF-based script.
			if ( cli.hasOption ( "offset" ) || cli.hasOption ( "limit" ) || sampleTabs == null && msiAccs == null )
			{
				if ( rndQuota != null ) annService.setRandomSelectionQuota ( rndQuota );
				
				String offsetStr = cli.getOptionValue ( "offset" );
				String limitStr = cli.getOptionValue ( "limit" );
				annService.submit ( 
					offsetStr == null ? null : Integer.valueOf ( offsetStr ), 
					limitStr == null ? null : Integer.valueOf ( limitStr ) ,
						purgeFirst
				);
			}			
		}
		catch ( Throwable ex ) 
		{
			log.error ( "Execution failed with the error: " + ex.getMessage (), ex  );
			exitCode = 1; // TODO: proper exit codes
		}
		finally 
		{
			// See NO_EXIT_PROP definition
			if ( "true".equals ( System.getProperty ( NO_EXIT_PROP ) ) )
				terminationHandler ();
			else
				System.exit ( exitCode );
		}
	}
	
	/**
	 * Last operations, before JVM shutdown (or the end of a JUnit test). 
	 * flushes the in-memory annotations that were gathered so far to the DB, Closes the entity manager factory.
	 * 
	 */
	private static void terminationHandler ()
	{
		if ( getExitCode () == 128 ) return; // --help option
		if ( getExitCode () == 127 ) { // --get stats option
			annService.waitAllFinished (false);
			EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
			if ( emf != null && emf.isOpen () ) emf.close ();
			return;
		}

		if ( annService != null ) 
		{
			annService.waitAllFinished ();
			
			log.info ( getExitCode () == 0 
				? "Computations should have gone fine!"
				: "Some problems occurred, but some data were saved"
			);
		}

		if ( "true".equals ( System.getProperty ( NO_EXIT_PROP ) ) ) return;

		EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
		if ( emf != null && emf.isOpen () ) emf.close ();		
	}
	
	
	
	@SuppressWarnings ( "static-access" )
	private static Options getOptions ()
	{
		Options opts = new Options ();

		opts.addOption ( OptionBuilder
			.withDescription ( "annotates all the sample (group) properties related to a given submission. WARNING!!!: Purges the annotated attribute values before re-annotating them. This could either be a file (path/filename) that contains the accessions you wish to annotate on each line, or the accession directly passed as an argument" )
			.withLongOpt ( "submission" )
			.withArgName ( "accession" )
			.hasArg ()
			.create ( 's' )
		);
		
		opts.addOption ( OptionBuilder
			.withDescription ( "like --submission, takes the submission accession from the SampleTab submission file" )
			.withLongOpt ( "sampletab" )
			.withArgName ( "path" )
			.hasArg ()
			.create ( 't' )
		);
		
		opts.addOption ( OptionBuilder
			.withDescription ( "annotates the property value table, starting from this offset (used by the LSF command)" )
			.withLongOpt ( "offset" )
			.withArgName ( "0-n" )
			.hasArg ()
			.create ( 'o' )
		);
		
		opts.addOption ( OptionBuilder
			.withDescription ( "returns the number of records in the property value table"
					+ " (used by the LSF command, incompatible with other options)" )
			.withLongOpt ( "property-count" )
			.create ( 'p' )
		);
		
		
		opts.addOption ( OptionBuilder
			.withDescription ( "to be used with --offset, the number of property records from the offset to be annotated"
					+ " (incompatible with --submission or --sampletab)" )
			.withLongOpt ( "limit" )
			.withArgName ( "1-n" )
			.hasArg ()
			.create ( 'l' )
		);


		opts.addOption ( OptionBuilder
			.withDescription ( 
				"picks up a random subset of property values, expressed in percentage of the total"
				+ " (removes a random subset when passed to --purge)" )
			.withLongOpt ( "random-quota" )
			.withArgName ( "0-100" )
			.hasArg ()
			.create ( 'r' )
		);

		opts.addOption ( OptionBuilder
			.withDescription ( "remove entries created by the annotator, which are older than <age-days> (default is 90),"
				+ " so that annotations can be updated (incompatible with other options, except --random-quota)." 
			)
			.withLongOpt ( "purge" )
			.withArgName ( "age-days" )
			.hasOptionalArg ()
			.create ( 'g' )
		);

		opts.addOption ( OptionBuilder
			.withDescription ( 
				"Removes lock records from the database, used to synchronise processes in clustering/LSF mode. This might be"
				+ " useful if the execution of annotate_lsf.sh seems to hang up. STOP any annotator job on LSF before running"
				+ " this option!"
			)
			.withLongOpt ( "unlock" )
			.create ( 'k' )
		);
		
		opts.addOption ( OptionBuilder
			.withDescription ( "prints out this message" )
			.withLongOpt ( "help" )
			.create ( 'h' )
		);

		opts.addOption ( OptionBuilder
				.withDescription ( "Purges the values that are already annotated before running the annotator" )
				.withLongOpt ( "first-purge" )
				.create ("fp")
		);

		opts.addOption ( OptionBuilder
				.withDescription ( "To be used with -dirLocation and (-compare and/or -printAnnotations). The file with submission accessions (one on each line), whose annotations we want to export." )
				.withLongOpt ( "submissionAccessions" )
				.hasArg ()
				.create ("acc")
		);

		opts.addOption ( OptionBuilder
				.withDescription ( "To be used with -submissionAccessions and -dirLocation. \n Prints the property values and annotations if the discovered annotation differs from the resolved annotation. \n Stored in the file \"CompareResolvedWithDiscovered.txt\" in the directory specified. NOTE: Appends to file!" )
				.withLongOpt ( "compare" )
				.create ("c")
		);

		opts.addOption ( OptionBuilder
				.withDescription ( "To be used with -submissionAccessions and -dirLocation. For each submission, a filed called Acc_<submission_accession> will be created in the directory specified, with all its annotations exported for each property value that belongs to this sumbission." )
				.withLongOpt ( "printAnnotations" )
				.create ("p")
		);

		opts.addOption ( OptionBuilder
				.withDescription ( "To be used with -submissionAccessions. The directory location (path) for the submission stat files to be stored in." )
				.withLongOpt ( "dirLocation" )
				.hasArg ()
				.create ("dl")
		);

		return opts;		
	}
	
	private static void printUsage ()
	{
		out.println ();

		out.println ( "\n\n *** BioSD Feature Annotator ***" );
		out.println ( "\nAnnotates biosample attributes with ontology references (computed via ZOOMA and OLS) and numeric structures." );
		
		out.println ( "\nSyntax:" );
		out.println ( "\n\tannotate.sh [options]" );		
		
		out.println ( "\nOptions:" );
		HelpFormatter helpFormatter = new HelpFormatter ();
		PrintWriter pw = new PrintWriter ( out, true );
		helpFormatter.printOptions ( pw, 100, getOptions (), 2, 4 );
		
		out.println ( "\nRelevant Environment Variables:" );
		//out.println ( "  OPTS=\"$OPTS -D" + PropertyValAnnotationManager.ONTO_DISCOVERER_PROP_NAME + "=<zooma|bioportal>\": which text/ontology annotator to use" );
		//out.println ();
		out.println ( "  OPTS=\"$OPTS -D" + PropertyValAnnotationService.MAX_THREAD_PROP + "=<num>\": max number of threads that can be used" );
		out.println ( "  (very important in LSF mode)" );
		out.println ();
		out.println ( "  OPTS=\"$OPTS -D" + AnnotatorPersister.LOCK_TIMEOUT_PROP + "=<s>\": timeout for LSF saving lock record (see -k, default is 30min)." );
		out.println ( "\n\n" );
		
		exitCode = 128;
	}

	/**
	 * This can be used when {@link #NO_EXIT_PROP} is "true" and you're invoking {@link #main(String...)} from 
	 * a JUnit test. It tells you the OS exit code that the JVM would return upon exit.
	 */
	public static int getExitCode ()
	{
		return exitCode;
	}

}
