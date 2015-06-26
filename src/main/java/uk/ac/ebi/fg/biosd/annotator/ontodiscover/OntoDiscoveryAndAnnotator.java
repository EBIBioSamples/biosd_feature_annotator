package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import org.apache.commons.lang3.tuple.Pair;

import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fgpt.zooma.search.ZOOMASearchClient;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntoTermDiscoveryMemCache;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>25 Jun 2015</dd>
 *
 */
public class OntoDiscoveryAndAnnotator
{	
	private final OntologyTermDiscoverer ontoTermDiscoverer;

	/**
	 * This is usually initialised with {@link ZOOMASearchClient}, by composing it in {@link BioSDCachedOntoTermDiscoverer}, 
	 * where {@link OntoTermDiscoveryMemCache} is used at the first level, and {@link BioSDOntoDiscoveringCache} is used 
	 * at the second level. 
	 */
	public OntoDiscoveryAndAnnotator ( OntologyTermDiscoverer ontoTermDiscoverer )
	{
		// Double cache results in memory and in the BioSD database.
		this.ontoTermDiscoverer = ontoTermDiscoverer; 
	}
	
	public void annotate ( ExperimentalPropertyValue<ExperimentalPropertyType> pval, boolean isNumberOrDate )
	{
		if ( pval == null ) return;
		
		Pair<String, String> pair = ExpPropValAnnotation.getTypeAndVal ( pval );
		if ( pair == null ) return; 

		String pvalTypeLabel = pair.getLeft (), pvalLabel = pair.getRight ();
		
		if ( isNumberOrDate && pvalTypeLabel != null )
			ontoTermDiscoverer.getOntologyTermUris ( pvalTypeLabel, null );
		else
			ontoTermDiscoverer.getOntologyTermUris ( pvalLabel, pvalTypeLabel );
	}

}
