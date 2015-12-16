package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.ac.ebi.onto_discovery.api.CachedOntoTermDiscoverer;
import uk.ac.ebi.onto_discovery.api.OntoTermDiscoveryCache;
import uk.ac.ebi.onto_discovery.api.OntologyDiscoveryException;
import uk.ac.ebi.onto_discovery.api.OntologyTermDiscoverer;

import com.opencsv.CSVReader;;

/**
 * An adapter of {@link CachedOntoTermDiscoverer} to the special cases we have in BioSD.
 * @author brandizi
 *
 */
public class BioSDCachedOntoTermDiscoverer extends CachedOntoTermDiscoverer
{
	/**
	 * This is loaded from a file, containing a list of property type labels, which of values are known to not being associated to ontology
	 * terms, eg, 'sample id'. 
	 * 
	 */
	private static Set<String> nonOntologyTypes = null;
	
	// Initialise nonOntologyTypes from non_ontology_property_types.csv
	{
		synchronized ( BioSDCachedOntoTermDiscoverer.class )
		{
			try
			{
				if ( nonOntologyTypes == null )
				{
					nonOntologyTypes = new HashSet<String> ();
					CSVReader csvReader = new CSVReader ( 
						new InputStreamReader ( this.getClass ().getResourceAsStream ( 
							"/non_ontology_property_types.csv" 
					)), '\t' );
					for ( 
						String line[] = csvReader.readNext (); 
						( line = csvReader.readNext () ) != null; 
						nonOntologyTypes.add ( line [ 0 ].trim ().toLowerCase () ) 
					);
					csvReader.close ();
				}
			}
			catch ( IOException ex )
			{
				throw new RuntimeException ( 
					"Internal Error while trying to fetch internal file non_ontology_property_types.csv: " + ex.getMessage (), ex 
				);
			}
		}
	}

	public BioSDCachedOntoTermDiscoverer ( OntologyTermDiscoverer base, OntoTermDiscoveryCache cache )
		throws IllegalArgumentException
	{
		super ( base, cache );
	}


	public BioSDCachedOntoTermDiscoverer ( OntologyTermDiscoverer base )
	{
		super ( base );
	}
	

	/**
	 * If typeLabel is in {@link #nonOntologyTypes}, it changes the value to the type and the type to null, 
	 * in the hope to find something for the type only.
	 * 
	 * TODO: JUnit test
	 * 
	 */
	@Override
	public List<DiscoveredTerm> getOntologyTerms ( String valueLabel, String typeLabel ) throws OntologyDiscoveryException
	{
		if ( "Å".equals ( valueLabel ) )
			// This is never correctly mapped, by either ontology lookup, or text annotators, because UO has 'A' attached
			// to UO_0000019, not the right symbol
			return Arrays.asList ( new DiscoveredTerm ( 
				"http://purl.obolibrary.org/obo/UO_0000019", 100d, "Å", "BioSD Feature Annotator" 
			));
			
		if ( typeLabel != null && nonOntologyTypes.contains ( typeLabel.trim ().toLowerCase () ) )
		{
			valueLabel = typeLabel;
			typeLabel = null;
		}
				
		return super.getOntologyTerms ( valueLabel, typeLabel );
	}
	
}
