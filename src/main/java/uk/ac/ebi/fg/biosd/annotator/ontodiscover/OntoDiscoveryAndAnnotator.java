package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotationManager;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.terms.AnnotationType;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.toplevel.AnnotationProvenance;
import uk.ac.ebi.fg.core_model.toplevel.TextAnnotation;
import uk.ac.ebi.fg.core_model.utils.toplevel.AnnotationUtils;
import uk.ac.ebi.fgpt.zooma.search.ZOOMASearchClient;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntoTermDiscoveryMemCache;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer.DiscoveredTerm;
import uk.ac.ebi.utils.regex.RegEx;

/**
 * Discovers ontology terms via ZOOMA and annotates {@link ExperimentalPropertyValue}. 
 * 
 * @author brandizi
 * <dl><dt>Date:</dt><dd>15 Dec 2014</dd>
 *
 */
public class OntoDiscoveryAndAnnotator
{
	private final static RegEx COMMENT_RE = new RegEx ( "(Comment|Characteristic)\\s*\\[\\s*(.+)\\s*\\]", Pattern.CASE_INSENSITIVE );
	
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
	 * Does what explained in the constructor. 
	 */
	public void annotate ( ExperimentalPropertyValue<ExperimentalPropertyType> pval, boolean isNumberOrDate )
	{
		TextAnnotation zoomaEmptyMappingMarker = createEmptyZoomaMappingMarker ();
		
		// Do we already know that this doesn't map to anything? This is a bit redundant, but allows us to safely deal
		// with properties picked from samples/groups/submissions
		if ( !AnnotationUtils.find ( 
			pval.getAnnotations (), null, zoomaEmptyMappingMarker.getType ().getName (), false, true 
		).isEmpty () )
			return;

		// This are the ontology terms associated to the property value by ZOOMA
		List<DiscoveredTerm> zterms = getOntoClassUris ( pval, isNumberOrDate );
		
		if ( zterms.isEmpty () )
		{
			// Doh! There isn't anything for this PV, let's trace this results too, so that we won't repeat it next time
      pval.addAnnotation ( zoomaEmptyMappingMarker );
			return;
		}

		// Attach the terms found for this properties
		for ( DiscoveredTerm dterm: getOntoClassUris ( pval, isNumberOrDate ) )
		{
			OntologyEntry oe = ((ExtendedDiscoveredTerm) dterm).getOntologyTerm ();
			// This will save the term, if it's new.
			pval.addOntologyTerm ( oe );
		}
	}

	/**
	 * This will get the ontology terms associated to the property value, either from ZOOMA, or the previous
	 * ZOOMA results stored in BioSD, or the memory cache.
	 * 
	 * The method also takes into account if the property value has already been deemed to be a number/range/date, in which 
	 * case it will only use its {@link ExperimentalPropertyType type}, to annotate the numberish value with a type.
	 * 
	 */
	public List<DiscoveredTerm> getOntoClassUris ( ExperimentalPropertyValue<?> pval, boolean isNumberOrDate ) 
	{
		if ( pval == null ) return Collections.emptyList ();
		
		String pvalLabel = pval.getTermText ();
		
		ExperimentalPropertyType ptype = pval.getType ();
		if ( ptype == null ) return Collections.emptyList (); 
		
		String pvalTypeLabel = getExpPropTypeLabel ( ptype );
		if ( pvalTypeLabel == null ) return null;
		
		// Next, see if it is a number
		// Try to resolve the type instead of the value, use both value and type if it's not a number
		return isNumberOrDate && pvalTypeLabel != null
			? ontoTermDiscoverer.getOntologyTermUris ( pvalTypeLabel, null )
			:	ontoTermDiscoverer.getOntologyTermUris ( pvalLabel, pvalTypeLabel );
	}

	
	/**
	 * Extract the type label, considering things like Comment [ X ] (return 'X' in such cases).
	 */
	public static String getExpPropTypeLabel ( ExperimentalPropertyType ptype ) 
	{
		if ( ptype == null ) return null;
		String typeLabel = StringUtils.trimToNull ( ptype.getTermText () );
		if ( typeLabel == null ) return null;
		
		String[] chunks = COMMENT_RE.groups ( typeLabel );
		if ( chunks == null || chunks.length < 3 ) return typeLabel;
		return chunks [ 2 ];
	}
	
	public static TextAnnotation createEmptyZoomaMappingMarker ()
	{
		TextAnnotation result = new TextAnnotation ( 
			new AnnotationType ( "ZOOMA Marker for null mapping to Ontology Terms" ),
			null 
		);
		
		result.setProvenance ( new AnnotationProvenance ( PropertyValAnnotationManager.PROVENANCE_MARKER ) );
		result.setTimestamp ( new Date () );
		
		AnnotatorResources.getInstance ().getAnnNormalizer ().normalize ( result );

		return result;
	}

}
