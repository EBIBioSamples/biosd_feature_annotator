package uk.ac.ebi.fg.biosd.annotator.cli;

import static java.lang.System.out;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.threading.PropertyValAnnotationService;

/**
 * 
 * TODO: Comment me!
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

	
	
	public static void main ( String... args )
	{
		try
		{
			CommandLineParser clparser = new GnuParser ();
			CommandLine cli = clparser.parse ( getOptions(), args );
			
			int xopts = 0;
			if ( cli.hasOption ( "help" ) )
				xopts = 2;
			else 
			{
				if ( cli.hasOption ( "submission" ) || cli.hasOption ( "sampletab" ) ) xopts++;
				if ( cli.hasOption ( "offset" ) || cli.hasOption ( "limit" ) ) xopts++;
				if ( cli.hasOption ( "property-count" ) )
				{
					if ( cli.hasOption ( "random-quota" ) )
						xopts = 2;
					else
						xopts++;
				}
			}
			
			if ( xopts > 1 ) 
			{
				printUsage ();
				return;
			}
			
			
			PropertyValAnnotationService annService = new PropertyValAnnotationService ();
			
		  if ( cli.hasOption ( "property-count" ) )
		  {
		  	out.println ( annService.getPropValCount () );
				log.info ( "all went fine!" );
		  	return;
		  }
			
		  
			String msiAccs[] = cli.getOptionValues ( "submission" );
			if ( msiAccs != null )
			{
				for ( String msiAcc: msiAccs )
					annService.submitMSI ( msiAcc );
			}

			String sampleTabs[] = cli.getOptionValues ( "sampletab" );
			if ( sampleTabs != null )
			{
				for ( String sampleTab: sampleTabs )
					annService.submitMSI ( new FileInputStream ( new File ( sampleTab ) ) );
			}
			
			if ( cli.hasOption ( "offset" ) || cli.hasOption ( "limit" ) || sampleTabs == null && msiAccs == null )
			{
				Double rndQuota = Double.valueOf ( cli.getOptionValue ( "random-quota", "100.0" ) );
				if ( rndQuota < 100.0 && rndQuota >= 0.0 ) annService.setRandomSelectionQuota ( rndQuota / 100.0 );
				
				String offsetStr = cli.getOptionValue ( "offset" );
				String limitStr = cli.getOptionValue ( "limit" );
				annService.submit ( 
					offsetStr == null ? null : Integer.valueOf ( offsetStr ), 
					limitStr == null ? null : Integer.valueOf ( limitStr ) 
				);
			}
			
		  annService.waitAllFinished ();
			log.info ( "all should have gone fine!" );
		}
		catch ( Throwable ex ) 
		{
			log.error ( "Execution failed with the error: " + ex.getMessage (), ex  );
			exitCode = 1; // TODO: proper exit codes
		}
		finally 
		{
			if ( !"true".equals ( System.getProperty ( NO_EXIT_PROP ) ) )
				System.exit ( exitCode );
		}
	}
	
	@SuppressWarnings ( "static-access" )
	private static Options getOptions ()
	{
		Options opts = new Options ();

		opts.addOption ( OptionBuilder
			.withDescription ( "annotates all the sample (group) properties related to a given submission" )
			.withLongOpt ( "submission" )
			.withArgName ( "submission accession" )
			.hasArg ()
			.create ( 's' )
		);
		
		opts.addOption ( OptionBuilder
			.withDescription ( "like --submission, takes the submission accession from the SampleTab submission file" )
			.withLongOpt ( "sampletab" )
			.withArgName ( "submission accession" )
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
			.withDescription ( "picks up a random subset of property values, expressed in percentage of the total" )
			.withLongOpt ( "random-quota" )
			.withArgName ( "0-100" )
			.hasArg ()
			.create ( 'r' )
		);

		opts.addOption ( OptionBuilder
			.withDescription ( "Prints out this message" )
			.withLongOpt ( "help" )
			.create ( 'h' )
		);
		
		return opts;		
	}
	
	private static void printUsage ()
	{
		out.println ();

		out.println ( "\n\n *** BioSD Feature Annotator ***" );
		out.println ( "\nAnnotates biosample attributes with ontology references (computed via ZOOMA and Bioportal) and numeric structures." );
		
		out.println ( "\nSyntax:" );
		out.println ( "\n\tannotate.sh [options]" );		
		
		out.println ( "\nOptions:" );
		HelpFormatter helpFormatter = new HelpFormatter ();
		PrintWriter pw = new PrintWriter ( out, true );
		helpFormatter.printOptions ( pw, 100, getOptions (), 2, 4 );
		
		out.println ( "\n\n" );
		
		exitCode = 1;
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
