package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.apache.commons.lang.StringUtils;

import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer.DiscoveredTerm;
import uk.ac.ebi.utils.regex.RegEx;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>15 Dec 2014</dd>
 *
 */
public class OntoDiscoveryAndAnnotator
{
	private final static RegEx COMMENT_RE = new RegEx ( "(Comment|Characteristic)\\s*\\[\\s*(.+)\\s*\\]", Pattern.CASE_INSENSITIVE );
	
	private final OntologyTermDiscoverer ontoTermDiscoverer;

	public OntoDiscoveryAndAnnotator ( OntologyTermDiscoverer ontoTermDiscoverer )
	{
		// Double cache results in memory and in the BioSD database.
		this.ontoTermDiscoverer = ontoTermDiscoverer; 
	}
	
	public void annotate ( ExperimentalPropertyValue<ExperimentalPropertyType> pval, boolean isNumberOrDate, EntityManager em )
	{
		// This are the ontology terms associated to the property value by ZOOMA
		for ( DiscoveredTerm dterm: getOntoClassUris ( pval, isNumberOrDate ) )
		{
			EntityTransaction tx = em.getTransaction ();
			tx.begin ();
			OntologyEntry oe = ((ExtendedDiscoveredTerm) dterm).getOntologyTerm ();
			// This will save the term, if it's new.
			oe = em.merge ( oe );
			pval = em.merge ( pval );
			pval.addOntologyTerm ( oe );
			tx.commit ();
		}
	}

	/**
	 * This will get the ontology terms associated to the property value, either from ZOOMA, or the previous
	 * ZOOMA results stored in BioSD, or the memory cache.
	 * 
	 * The method also takes into account if the property value has already been deemed to be a number/range/date, in which 
	 * case it will only use its {@link ExperimentalPropertyType type}, to annotate the numberish value with a type.
	 * 
	 * This last part is TODO.
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
		return isNumberOrDate 
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

}
