package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import java.util.Date;
import java.util.List;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotationManager;
import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.onto_discovery.api.CachedOntoTermDiscoverer;
import uk.ac.ebi.onto_discovery.api.OntoTermDiscoveryCache;
import uk.ac.ebi.onto_discovery.api.OntologyDiscoveryException;

import com.google.common.collect.Table;

/**
 * Caches discovered terms in memory, using {@link AnnotatorResources#getStore()}, so that they can later be saved
 * into the BioSD database.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>25 Jun 2015</dd>
 *
 */
public class OntoTermDiscoveryStoreCache extends OntoTermDiscoveryCache
{
	public static final String ANNOTATION_TYPE_MARKER = "Computed Annotation, via ZOOMA";

	@Override
	public List<DiscoveredTerm> save ( String valueLabel, String typeLabel, List<DiscoveredTerm> dterms ) 
		throws OntologyDiscoveryException
	{
		String pvkey = ExpPropValAnnotation.getPvalText ( typeLabel, valueLabel );
		if ( pvkey == null ) return CachedOntoTermDiscoverer.NULL_RESULT;
		
		Table<Class, String, Object> store = AnnotatorResources.getInstance ().getStore ();
		
		// This is needed by this cache and ignored during the persistence stage (ExpPropValAnnotation are considered instead)
		store.put ( DiscoveredTerm.class, pvkey, dterms );

		if ( dterms.isEmpty () )
		{
			// Store an annotation that traces the fact there's nothing for this key
			ExpPropValAnnotation pvann = new ExpPropValAnnotation ( pvkey );
			pvann.setOntoTermUri ( ExpPropValAnnotation.NULL_TERM_URI );
			pvann.setType ( ANNOTATION_TYPE_MARKER );
			pvann.setProvenance ( PropertyValAnnotationManager.PROVENANCE_MARKER );
			pvann.setTimestamp ( new Date () );
			store.put ( ExpPropValAnnotation.class, pvkey, pvann ); 
			
			return CachedOntoTermDiscoverer.NULL_RESULT;
		}
		
		// Else, store an annotation for each found term
		for ( DiscoveredTerm dterm: dterms )
		{
			String uri = dterm.getIri ();
			
			ExpPropValAnnotation pvann = new ExpPropValAnnotation ( pvkey );
			pvann.setType ( ANNOTATION_TYPE_MARKER );
			pvann.setProvenance ( PropertyValAnnotationManager.PROVENANCE_MARKER );
			pvann.setOntoTermUri ( uri );
			pvann.setScore ( dterm.getScore () ); 
			pvann.setTimestamp ( new Date () );

			store.put ( ExpPropValAnnotation.class, pvkey + ":" + uri, pvann ); 
		}
		
		return dterms;
	}

	@SuppressWarnings ( { "rawtypes", "unchecked" } )
	@Override
	public List<DiscoveredTerm> getOntologyTerms ( String valueLabel, String typeLabel )
		throws OntologyDiscoveryException
	{
		String pvkey = ExpPropValAnnotation.getPvalText ( typeLabel, valueLabel );
		if ( pvkey == null ) return CachedOntoTermDiscoverer.NULL_RESULT;
		
		Table<Class, String, Object> store = AnnotatorResources.getInstance ().getStore ();
		return (List<DiscoveredTerm>) store.get ( DiscoveredTerm.class, pvkey );
	}

}
