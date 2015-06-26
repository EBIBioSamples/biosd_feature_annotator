package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;

import org.hibernate.Session;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotationManager;
import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.fg.biosd.annotator.persistence.SynchronizedStore;
import uk.ac.ebi.fg.biosd.annotator.purge.Purger;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.MemoryStore;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.normalizers.toplevel.AnnotationNormalizer;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.AnnotationType;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.toplevel.Annotatable;
import uk.ac.ebi.fg.core_model.toplevel.Annotation;
import uk.ac.ebi.fg.core_model.toplevel.AnnotationProvenance;
import uk.ac.ebi.fg.core_model.toplevel.TextAnnotation;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.CachedOntoTermDiscoverer;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntoTermDiscoveryCache;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyDiscoveryException;

/**
 * 
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>25 Jun 2015</dd>
 *
 */
public class BioSDOntoDiscoveringCache extends OntoTermDiscoveryCache
{
	public final static String NULL_TERM_URI = "http://rdf.ebi.ac.uk/terms/biosd/NullOntologyTerm";
	
	@Override
	public List<DiscoveredTerm> save ( String valueLabel, String typeLabel, List<DiscoveredTerm> dterms )
		throws OntologyDiscoveryException
	{
		return dterms;
	}

	

	@Override
	@SuppressWarnings ( "unchecked" )
	public List<DiscoveredTerm> getOntologyTermUris ( String valueLabel, String typeLabel ) throws OntologyDiscoveryException
	{
		String pvkey = ExpPropValAnnotation.getPvalText ( typeLabel, valueLabel );
		if ( pvkey == null ) return CachedOntoTermDiscoverer.NULL_RESULT;

		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();

		try
		{
			Session session = (Session) em.getDelegate ();
			List<ExpPropValAnnotation> pvanns = session
				.getNamedQuery ( "findByPv" )
				.setReadOnly ( true )
				.setParameter ( "pvkey", pvkey )
				.list ();
			
			if ( pvanns == null || pvanns.isEmpty () ) return CachedOntoTermDiscoverer.NULL_RESULT;
			
			List<DiscoveredTerm> result = new ArrayList<DiscoveredTerm> ();
			for ( ExpPropValAnnotation ann: pvanns )
				result.add ( new DiscoveredTerm ( new URI ( ann.getOntoTermUri () ), ann.getScore ().floatValue () ) );
			
			return result;
		}
		catch ( URISyntaxException ex )
		{
			// If this really happens, you're doomed, sorry
			throw new OntologyDiscoveryException (
				String.format ( 
					"Error while fetching ZOOMA annotation from the BioSD DB for '%s'/'%s': %s", 
					valueLabel, typeLabel, ex.getMessage () 
				),
				ex 
			);
		}
		finally {
			if ( em.isOpen () ) em.close ();
		}
	}
	
}
