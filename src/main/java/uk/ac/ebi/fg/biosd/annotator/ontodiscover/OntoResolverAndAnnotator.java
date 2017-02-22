package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import java.util.Date;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotationManager;
import uk.ac.ebi.fg.biosd.annotator.model.ComputedOntoTerm;
import uk.ac.ebi.fg.biosd.annotator.model.ResolvedOntoTermAnnotation;
import uk.ac.ebi.fg.core_model.terms.FreeTextTerm;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.xref.ReferenceSource;

import com.google.common.collect.Table;

/**
 * "Resolves" {@link OntologyEntry} or OE's attached to 
 * {@link FreeTextTerm#getOntologyTerms() OE's attached to free-text objects}. This means that explicit annotations are
 * checked against an ontology lookup service (such as Bioportal) and proper annotations are linked to the initial 
 * parameter and stored into the {@link AnnotatorResources#getStore() annotator's store}.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>25 Jun 2015</dd>
 *
 */
public class OntoResolverAndAnnotator
{
	public final static String ANNOTATION_TYPE_MARKER = "Computed from original annotation, via OLS";
	
	private final Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	/*static {
		BioportalWebServiceUtils.STATS_WRAPPER.setPopUpExceptions ( false );
	}
	*/
	
	public OntoResolverAndAnnotator () {}
	
	/**
	 * Annotates the {@link OntologyEntry} attached to the property value, as explained above.
	 */
	public boolean annotate ( FreeTextTerm term )
	{
		Set<OntologyEntry> oes = term.getOntologyTerms ();
		if ( oes == null ) return false;
		
		boolean result = false;
		
		for ( OntologyEntry oe: oes ) 
			result |= resolveOntoTerm ( oe );
		
		return result;
	} 
	

	/**
	 * @return true if the term is actually associated to another ontology term computed via ontology lookup.
	 */
	private boolean resolveOntoTerm ( OntologyEntry oe )
	{
		String acc = StringUtils.trimToNull ( oe.getAcc () );
		ReferenceSource src = oe.getSource ();
		String srcAcc = src == null ? null : StringUtils.trimToNull ( src.getAcc () );

		String oekey = ResolvedOntoTermAnnotation.getOntoEntryText ( oe );
		if ( oekey == null ) return false; // This OE cannot be annotated
		
		// Do you have it in memory?
		Table<Class, String, Object> store = AnnotatorResources.getInstance ().getStore ();
		ResolvedOntoTermAnnotation oeann = null;
		synchronized ( oekey.intern () )
		{
			oeann = (ResolvedOntoTermAnnotation) store.get ( ResolvedOntoTermAnnotation.class, oekey );
			// TODO: is it in the DB then? If yes, return that
		
			if ( oeann != null ) return oeann.getOntoTermUri () != null;

			// Annotation for origin and provenance
			oeann = new ResolvedOntoTermAnnotation ( oekey );
			oeann.setType ( ANNOTATION_TYPE_MARKER );
			oeann.setProvenance ( PropertyValAnnotationManager.PROVENANCE_MARKER );
			oeann.setTimestamp ( new Date () );
			

			String uri = null;
			

			// Save in memory store, for lookup and later persistence
			store.put ( ResolvedOntoTermAnnotation.class, oekey, oeann );

			return uri != null;
		} // synchronized ( oekey )
		
	} // resolveOntoTerms ( oe )

}
