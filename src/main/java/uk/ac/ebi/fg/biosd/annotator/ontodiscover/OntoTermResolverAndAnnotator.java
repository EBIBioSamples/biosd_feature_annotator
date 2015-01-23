package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import java.util.Date;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.bioportal.webservice.client.BioportalClient;
import uk.ac.ebi.bioportal.webservice.exceptions.OntologyServiceException;
import uk.ac.ebi.bioportal.webservice.model.OntologyClass;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.DBStore;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.normalizers.toplevel.AnnotationNormalizer;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.terms.AnnotationType;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.toplevel.Annotation;
import uk.ac.ebi.fg.core_model.toplevel.AnnotationProvenance;
import uk.ac.ebi.fg.core_model.toplevel.Identifiable;
import uk.ac.ebi.fg.core_model.toplevel.TextAnnotation;
import uk.ac.ebi.fg.core_model.xref.ReferenceSource;
import uk.ac.ebi.utils.reflection.ReflectionUtils;

/**
 * This uses the {@link BioportalClient} to verify the explicit (i.e., not discovered via ZOOMA) {@link OntologyEntry} 
 * annotations attached to an {@link ExperimentalPropertyValue}. In case they're acknowledged ontology terms, a proper 
 * annotation is added to the ontology entry object, to track the fact the annotator has been here. Moreover, further 
 * details obtained by Bioportal are added (i.e., the term URI, when the term is specified via source+label).
 *
 * <dl><dt>date</dt><dd>19 Nov 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class OntoTermResolverAndAnnotator
{
	private final BioportalClient bioportalClient = new BioportalClient ( "07732278-7854-4c4f-8af1-7a80a1ffc1bb" );

	private final Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	
	public OntoTermResolverAndAnnotator ()
	{
		super ();
	}
	
	
	public boolean annotate ( ExperimentalPropertyValue<ExperimentalPropertyType> pv, EntityManager em )
	{
		Set<OntologyEntry> oes = pv.getOntologyTerms ();
		if ( oes == null ) return false;
		
		boolean result = false;
		for ( OntologyEntry oe: oes )
			result |= resolveOntoTerm ( oe, em );
		
		return result;
	} // resolveOntoTerms ( pv )
	
	
	
	public boolean resolveOntoTerm ( OntologyEntry oe, EntityManager em )
	{
		TextAnnotation zoomaMarker = BioSDOntoDiscoveringCache.createZOOMAMarker ( "foo", "foo" );
		TextAnnotation resolverMarker = createOntoResolverMarker ( "foo", "foo", "foo" );

		Date now = new Date ();
				
		AnnotationNormalizer<Annotation> annNormalizer = new AnnotationNormalizer<Annotation> ( new DBStore ( em ) );

		boolean annFound = false;

		// Check if it's already an auto-computed annotation 
		Set<Annotation> anns = oe.getAnnotations ();
		if ( anns != null ) for ( Annotation ann: anns )
		{
			AnnotationType annType = ann.getType ();
			if ( annType == null ) continue;
			
			if ( !resolverMarker.getType ().getName ().equals ( annType.getName () ) )
				continue;
		
			if ( zoomaMarker.getProvenance ().getName ().equals ( ann.getProvenance ().getName () ) )
				continue;
			
			annFound = true; break;
		}
		
		// Already annotated, let's go ahead.
		if ( annFound ) return false;
		
		// So, it needs a check with the ontology service
		String acc = StringUtils.trimToNull ( oe.getAcc () );
		ReferenceSource src = oe.getSource ();
		String srcAcc = src == null ? null : StringUtils.trimToNull ( src.getAcc () );
		
		final OntologyClass bpOntoTerm = getOntoTermUri ( srcAcc, acc );
		
		if ( bpOntoTerm == null ) return false;
		
		final String oldLabel = oe.getLabel ();
		
		// Get a new OE with the same ID, so that we may send updates.
		OntologyEntry newOe = new OntologyEntry ( bpOntoTerm.getIri (), null );
		newOe.setLabel ( bpOntoTerm.getPreferredLabel () );
		// Protected method
		ReflectionUtils.invoke ( newOe, Identifiable.class, "setId", new Class<?>[] { Long.class }, oe.getId () );
		
		TextAnnotation marker = createOntoResolverMarker ( acc, srcAcc, oldLabel, now ) ;
		
		EntityTransaction tx = em.getTransaction ();
		tx.begin ();

		// Add up marking annotation, which will also store initial values.
		// Normally the annotation normaliser is triggered by the ontology normaliser, however this doesn't happen here,
		// cause the ontology entry might already exist (ie, a new annotation about a new string pair is being added) 
		//
		annNormalizer.normalize ( marker );
		
		// Save the changes
		em.persist ( marker );
		//em.merge ( newOe );
		newOe.addAnnotation ( marker );
		em.merge ( newOe );

		tx.commit ();		

		return true;
		
	} // resolveOntoTerms ( oe )
	
	
	/**
	 * Uses {@link BioportalClient} to resolve an ontology term, specified via accession + ontology acronym.   
	 */
	public OntologyClass getOntoTermUri ( String srcAcc, String acc )
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

	public static TextAnnotation createOntoResolverMarker ( String initialAcc, String initialSrc, String initialLabel, Date timestamp )
	{
		TextAnnotation result = new TextAnnotation ( 
			new AnnotationType ( "Rewritten via Ontology Service lookup" ),
			String.format ( "initial accession: '%s', initial source: '%s', initial label: '%s'", initialAcc, initialSrc, initialLabel ) 
		);
		
		result.setProvenance ( new AnnotationProvenance ( BioSDOntoDiscoveringCache.PROVENANCE_MARKER ) );
		result.setTimestamp ( timestamp );
		
		return result;
	}

	public static TextAnnotation createOntoResolverMarker ( String initialAcc, String initialSrc, String initialLabel )
	{
		return createOntoResolverMarker ( initialAcc, initialSrc, initialLabel, null );
	}
}