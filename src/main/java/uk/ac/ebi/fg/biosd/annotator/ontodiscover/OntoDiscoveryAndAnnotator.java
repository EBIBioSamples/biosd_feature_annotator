package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import org.apache.commons.lang3.tuple.Pair;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fgpt.zooma.search.ZOOMASearchClient;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.ZoomaOntoTermDiscoverer;
import uk.ac.ebi.onto_discovery.api.OntoTermDiscoveryMemCache;
import uk.ac.ebi.onto_discovery.api.OntologyTermDiscoverer;

/**
 * Discovers ontology terms associated to {@link ExperimentalPropertyValue sample property values}, using
 * and {@link OntologyTermDiscoverer}, such as {@link ZoomaOntoTermDiscoverer}.
 * 
 * TODO: we don't fetch labels, when {@link ZoomaOntoTermDiscoverer} is used. This would require something like 
 * Bioportal, since ZOOMA doesn't support that.
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
	
	/**
	 * Annotates the pval with discoveries made via {@link OntologyTermDiscoverer}. This causes the results to be 
	 * scored onto {@link AnnotatorResources#getStore()}.
	 */
	public void annotate ( ExperimentalPropertyValue<ExperimentalPropertyType> pval, boolean isNumberOrDate )
	{
		if ( pval == null ) return;
		
		Pair<String, String> pair = ExpPropValAnnotation.getTypeAndVal ( pval );
		if ( pair == null ) return; 

		String pvalTypeLabel = pair.getLeft (), pvalLabel = pair.getRight ();
		
		if ( isNumberOrDate && pvalTypeLabel != null )
			ontoTermDiscoverer.getOntologyTerms ( pvalTypeLabel, null );
		else
			ontoTermDiscoverer.getOntologyTerms ( pvalLabel, pvalTypeLabel );
	}

}
