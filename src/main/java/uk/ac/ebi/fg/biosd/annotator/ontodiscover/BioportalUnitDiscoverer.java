package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.bioportal.webservice.client.BioportalClient;
import uk.ac.ebi.bioportal.webservice.model.OntologyClass;
import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.onto_discovery.api.OntologyDiscoveryException;
import uk.ac.ebi.onto_discovery.bioportal.BioportalOntoTermDiscoverer;

/**
 * When Bioportal is set as ontology discoverer, this is used for fetching Unit Ontology mappings.
 * This is raher simple: it loads all the Unit Ontology (UO) terms upon first invocation, then 
 * it does simple label/synonym mapping.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>26 Jan 2016</dd></dl>
 *
 */
public class BioportalUnitDiscoverer extends BioportalOntoTermDiscoverer
{
	
	private Map<String, String> uoUnits = null;
	
	private Logger log = LoggerFactory.getLogger ( this.getClass () );

	public BioportalUnitDiscoverer ( BioportalClient bpClient )
	{
		super ( bpClient );
	}

	public BioportalUnitDiscoverer ( String bioportalApiKey )
	{
		super ( bioportalApiKey );
	}

	
	/**
	 * See above, this uses {@link #getUnitUris()}.
	 */
	@Override
	public List<DiscoveredTerm> getOntologyTerms ( String unitLabel, String ignoredLabel ) throws OntologyDiscoveryException
	{
		List<DiscoveredTerm> result = new ArrayList<> ();
		
		if ( "true".equals ( System.getProperty ( AnnotatorResources.FAST_MODE_DEBUG_PROP_NAME ) ) ) return result;

		// Search under the tree branching from uo:unit
		String unitUri = getUnitUris ().get ( unitLabel );
		if ( unitUri != null )
			result.add ( new DiscoveredTerm ( unitUri, 100d, unitLabel, "Bioportal" ) );
		
		return result;
	}

	/**
	 * Get mapping between lables and URIs in the Unit Ontology (UO). This is cached in memory (forever) upon the first 
	 * invocation.
	 *  
	 */
	private synchronized Map<String, String> getUnitUris () throws OntologyDiscoveryException
	{
		if ( uoUnits != null ) return uoUnits;

		uoUnits = new HashMap<> ();

		log.info ( "Loading UO units, please wait" );
		
		BioportalClient bpcli = this.getBioportalClient ();
		Set<OntologyClass> terms = bpcli.getClassDescendants ( "UO", "UO_0000000" );
		if ( terms == null || terms.isEmpty () ) throw new OntologyDiscoveryException (
			"No units found in UO via Bioportal"
		);

		for ( OntologyClass term: terms )
		{
			if ( term == null ) {
				log.warn ( "Ignoring null term returned by the Ontology Service" );
				continue;
			}

			String tlabel = StringUtils.trimToNull ( term.getPreferredLabel () );
			String uri = term.getIri ();

			this.cacheNewUOUnit ( tlabel, uri );

			if ( uri == null ) continue;

			for ( String slabel: term.getSynonyms () )
				this.cacheNewUOUnit ( slabel, uri );
		}

		return this.uoUnits;
	}

	/**
	 * Helper for {@link #getUnitUris()}.
	 */
	private void cacheNewUOUnit ( String unitLabel, String uri ) throws OntologyDiscoveryException
	{
		if ( unitLabel == null ) {
			log.warn ( "Ignoring unit term with null label, URI is: {}", uri );
			return;
		}
		if ( uri == null ) {
			log.warn ( "Ignoring unit term '{}' with no URI", unitLabel );
			return;
		}

		if ( "A".equals ( unitLabel ) && uri.endsWith ( "UO_0000019" ) )
			// This is angstrom and it's represented as an 'A', but the wrong synonim is returned
			unitLabel = "Å";
		else if ( "micrometre".equals ( unitLabel ) && uri.endsWith ( "UO_0000016" ) )
			// The URI is actually about millimeter, which is already mapped
			return;

		unitLabel = unitLabel.trim ();
		String storedUri = this.uoUnits.get ( unitLabel );

		if ( storedUri != null && !uri.equals ( storedUri ) ) throw new OntologyDiscoveryException ( String.format (
		  "For some reason UO contains two URIs for the label '%s': ('%s', '%s')", unitLabel, uri, storedUri
		));
		log.trace ( "Saving '{}', {}", unitLabel, uri );
		this.uoUnits.put ( unitLabel, uri );
	}
}
