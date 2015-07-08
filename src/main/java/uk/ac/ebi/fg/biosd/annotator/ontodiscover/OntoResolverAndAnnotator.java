package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import java.util.Date;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.bioportal.webservice.client.BioportalClient;
import uk.ac.ebi.bioportal.webservice.exceptions.OntologyServiceException;
import uk.ac.ebi.bioportal.webservice.model.OntologyClass;
import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotationManager;
import uk.ac.ebi.fg.biosd.annotator.model.ComputedOntoTerm;
import uk.ac.ebi.fg.biosd.annotator.model.ResolvedOntoTermAnnotation;
import uk.ac.ebi.fg.core_model.terms.FreeTextTerm;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.xref.ReferenceSource;

import com.google.common.collect.Table;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>25 Jun 2015</dd>
 *
 */
public class OntoResolverAndAnnotator
{
	public final static String ANNOTATION_TYPE_MARKER = "Computed from original annotation, via Bioportal";
	
	private final BioportalClient bioportalClient = new BioportalClient ( "07732278-7854-4c4f-8af1-7a80a1ffc1bb" );
	private final Logger log = LoggerFactory.getLogger ( this.getClass () );
	
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
	 * TODO: comment me again!
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
		Table<Class, String, Object> store = AnnotatorResources.getInstance ().getNewStore ();
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
			
			OntologyClass bpOntoTerm = getOntoTermUriFromBioportal ( srcAcc, acc );

			String uri = null;
			
			if ( bpOntoTerm != null )
			{
				uri = bpOntoTerm.getIri ();
				store.put ( ComputedOntoTerm.class, uri, new ComputedOntoTerm ( uri, bpOntoTerm.getPreferredLabel () ) );
			
				// else, it will stay null and the annotation will tell us it's null
				oeann.setOntoTermUri ( uri );
			}
			
			// Save in the memory store, for later persistence
			store.put ( ResolvedOntoTermAnnotation.class, oekey, oeann );

			return uri != null;
		} // synchronized ( oekey )
		
	} // resolveOntoTerms ( oe )
	
	
	/**
	 * Uses {@link BioportalClient} to resolve an ontology term, specified via accession + ontology acronym.   
	 */
	public OntologyClass getOntoTermUriFromBioportal ( String srcAcc, String acc )
	{
		// Currently Bioportal doesn't allow searches based on URI only
		if ( srcAcc == null ) return null; 
		
		// Normalise the accession into a format that can be adapted to the onto-service
		String accNum = acc;
		int idx = acc.lastIndexOf ( ':' );
		if ( idx == -1 ) idx = acc.lastIndexOf ( '_' );
		if ( idx != -1 ) accNum = acc.substring ( idx + 1 ); 
		
		OntologyClass result = null;
		String[] testAccs = new String[] { 
			acc,	// Try this anyway, cause sometimes we might have NCBI_9096 within EFO as ontology
			accNum,
			srcAcc + "_" + accNum, 
			srcAcc.toUpperCase () + "_" + accNum, 
			srcAcc.toLowerCase () + "_" + accNum, 
			srcAcc + ":" + accNum,
			srcAcc.toUpperCase () + ":" + accNum,
			srcAcc.toLowerCase () + ":" + accNum					
		};
			
		try
		{
			// Try out different combinations for the term accession, this is because the end users suck at using proper
			// term accessions and these are the variants they most frequently bother us with.
			for ( String testAcc: testAccs )
				if ( ( result = this.bioportalClient.getOntologyClass ( srcAcc.toUpperCase (), testAcc ) ) != null ) break;
		} 
		catch ( OntologyServiceException ex ) 
		{
			log.warn ( String.format ( 
				"Internal Error about the Ontology Service: %s, ignoring ( '%s', '%s') ", ex.getMessage (), acc, srcAcc ), ex 
			);
			result = null;
		}

		return result; 
	}
	
	/** 
	 * Used for debugging 
	 */
	private OntologyClass getMockupClass ( String srcAcc, String acc )
	{
		if ( "EFO".equalsIgnoreCase (  srcAcc ) && "0000270".equals ( acc ) )
			return new OntologyClass ( "http://www.ebi.ac.uk/efo/EFO_0000270" );
		
		return null;
	}
}