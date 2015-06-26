package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import java.util.List;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.CachedOntoTermDiscoverer;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntoTermDiscoveryCache;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyDiscoveryException;

import com.google.common.collect.Table;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>25 Jun 2015</dd>
 *
 */
public class OntoTermDiscoveryStoreCache extends OntoTermDiscoveryCache
{
	public static final String ANNOTATION_TYPE_MARKER = "Computed from ZOOMA";

	@Override
	public List<DiscoveredTerm> save ( String valueLabel, String typeLabel, List<DiscoveredTerm> dterms ) 
		throws OntologyDiscoveryException
	{
		String pvkey = ExpPropValAnnotation.getPvalText ( typeLabel, valueLabel );
		if ( pvkey == null ) return CachedOntoTermDiscoverer.NULL_RESULT;
		
		Table<Class, String, Object> store = AnnotatorResources.getInstance ().getNewStore ();
		store.put ( DiscoveredTerm.class, pvkey, dterms );
		
		return ( dterms.isEmpty () ) ? CachedOntoTermDiscoverer.NULL_RESULT : dterms;
		
			// We need to feed the memory store with this too, so prepare it.
			// TODO: move to persistence
//			ExpPropValAnnotation pvann = new ExpPropValAnnotation ( pvkey );
//			pvann.setType ( ANNOTATION_TYPE_MARKER );
//			pvann.setProvenance ( PropertyValAnnotationManager.PROVENANCE_MARKER );
//			pvann.setTimestamp ( new Date () );

		// TODO: Move to persistence!
//		Date now = new Date ();
//		for ( DiscoveredTerm dterm: dterms )
//		{
//			ExpPropValAnnotation pvann = new ExpPropValAnnotation ( pvkey );
//			pvann.setType ( ANNOTATION_TYPE_MARKER );
//			pvann.setProvenance ( PropertyValAnnotationManager.PROVENANCE_MARKER );
//			pvann.setTimestamp ( now );
//			pvann.setOntoTermUri ( dterm.getUri ().toASCIIString () );
//			pvann.setScore ( (double) dterm.getScore () );
//		}
		
	}

	@Override
	public List<DiscoveredTerm> getOntologyTermUris ( String valueLabel, String typeLabel )
		throws OntologyDiscoveryException
	{
		String pvkey = ExpPropValAnnotation.getPvalText ( typeLabel, valueLabel );
		if ( pvkey == null ) return CachedOntoTermDiscoverer.NULL_RESULT;
		
		Table<Class, String, Object> store = AnnotatorResources.getInstance ().getNewStore ();
		return (List<DiscoveredTerm>) store.get ( DiscoveredTerm.class, pvkey );
	}

}
