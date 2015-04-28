package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotationManager;
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
 * <p>An {@link OntoTermDiscoveryCache} that is based on the BioSD database and its object model.</p>
 *  
 * <p>When it is asked to save a term, this cache stores it as an {@link OntologyEntry}, plus an {@link TextAnnotation}
 * that track the ZOOMA provenance and which string pairs originated the ontology term.</p>
 * 
 * <p>Dually, {@link #getOntologyTermUris(String, String)} checks that an annotation with the parameter strings exist and,
 * if yes, fetches the linked ontology term.</p>
 * 
 * <p>Older ontology terms and annotations are supposed to be deleted periodically, via the command line 
 * (@see {@link Purger}), so that the DB-cached annotations are periodically refreshed.</p> 
 *
 * <dl><dt>date</dt><dd>1 Aug 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class BioSDOntoDiscoveringCache extends OntoTermDiscoveryCache
{
	/**
	 * <p>An {@link OntologyEntry} with this URI is a special case used to mark property-related texts as being mapped
	 * to nothing (we save this fact for caching purposes).</p>
	 * 
	 * <p>More precisely, every time this happens an {@link OntologyEntry} having this URI is created (or refreshed) and
	 * a new {@link TextAnnotation} attached to it, containing the strings that lead to a null mapping.</p> 
	 */
	public final static String NULL_TERM_URI = "http://rdf.ebi.ac.uk/terms/biosd/NullOntologyTerm";
	
	@Override
	public List<DiscoveredTerm> save ( String valueLabel, String typeLabel, List<DiscoveredTerm> dterms )
		throws OntologyDiscoveryException
	{
		try
		{
			if ( dterms.isEmpty () ) 
			{
				dterms = new ArrayList<DiscoveredTerm> ();
				// We need to feed the memory store with this too, so prepare it.
				dterms.add ( 
					new ExtendedDiscoveredTerm ( new URI ( NULL_TERM_URI ), -1f, new OntologyEntry ( NULL_TERM_URI, null ) ) 
				);
			}
		}
		catch ( URISyntaxException ex ) 
		{
			throw new RuntimeException (  
				String.format ( "Internal error, strangely '%s' is not accepted as a URI: %s", NULL_TERM_URI, ex.getMessage () ), 
				ex 
			);
		}
		

		AnnotatorResources annResources = AnnotatorResources.getInstance (); 
		MemoryStore store = ((SynchronizedStore) annResources.getStore ()).getBase ();
		AnnotationNormalizer<Annotation> annNormalizer = annResources.getAnnNormalizer ();
		
		for ( int i = 0; i < dterms.size (); i++ )
		{
			DiscoveredTerm dterm = dterms.get ( i );
			URI dtermUri = dterm.getUri ();
			String dtermUriStr = dterm.getUri ().toASCIIString ();
			OntologyEntry oterm;
			
			synchronized ( store )
			{
				oterm = (OntologyEntry) store.get ( OntologyEntry.class, dtermUri );
				
				// Is it in the store?
				if ( oterm == null )
				{
					oterm = new OntologyEntry ( dtermUriStr, null );
					// Save a new one then, whether it's from DB or completely new.
					store.put ( OntologyEntry.class, dtermUriStr, oterm );
				}
				
	
				// Annotate the origin of this mapping
				Annotation zoomaMarker = BioSDOntoDiscoveringCache.createZOOMAMarker ( 
					valueLabel, typeLabel, (double) dterm.getScore (), new Date () 
				);
	
				// For an existing onto-term, this tells that a new string pair is associated to it. 
				oterm.addAnnotation ( zoomaMarker );
			}

			if ( NULL_TERM_URI.equals ( dtermUriStr ) ) return CachedOntoTermDiscoverer.NULL_RESULT;
			dterms.set ( i, new ExtendedDiscoveredTerm ( dtermUri, dterm.getScore (), oterm ) );
		
		} // for dterms 
		
		return dterms;
	}

	

	/**
	 * Works as explained in the class comment.
	 */
	@Override
	@SuppressWarnings ( "unchecked" )
	public List<DiscoveredTerm> getOntologyTermUris ( String valueLabel, String typeLabel ) throws OntologyDiscoveryException
	{
		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();

		try
		{
			// Search if there are ontology terms to which this textual entry is mapped
			// 
			TextAnnotation zoomaMarker = createZOOMAMarker ( valueLabel, typeLabel );
			
			List<Object[]> dbentries =  (List<Object[]>) em.createNamedQuery ( "findOntoAnnotations" )
			  .setParameter ( "provenance", zoomaMarker.getProvenance ().getName () )
			  .setParameter ( "annotation", zoomaMarker.getText () )
			  .setHint ( "org.hibernate.readOnly", true )
				.getResultList ();
			
			// The text entry doesn't exist at all: we don't have it yet and therefore we have to report null
			if ( dbentries.isEmpty () ) return null;

			// Now turn the real result into the required return format
			List<DiscoveredTerm> result = new ArrayList<> ();

			for ( Object[] tuple: dbentries )
			{
				OntologyEntry oterm = (OntologyEntry) tuple [ 0 ];
				PropertyValAnnotationManager.initializeLazy ( (Annotatable) oterm );
				
				Double score = (Double) tuple [ 1 ];
				
				if ( NULL_TERM_URI.equals ( oterm.getAcc () ) )
				{
					// This entry is reported to map to an empty result, so return the corresponding value (an empty list)
					// Before, store this result in memory
					AnnotatorResources.getInstance ().getStore ().find ( oterm, NULL_TERM_URI );
					return CachedOntoTermDiscoverer.NULL_RESULT;
				}
				
				// else return the discovered term that correspond to this entry
				DiscoveredTerm dterm = new ExtendedDiscoveredTerm ( 
					new URI ( oterm.getAcc () ),  score.floatValue (), oterm 
				);
				result.add ( dterm );
			}
			
			// Here you are all the terms found in the DB cache
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
	
	
	/**
	 * Creates the TextAnnotation that marks an ontology entry computed via the ZOOMA tool, which is what we 
	 * do in the feature annotator. This has a constant marker in {@link AnnotationType} and {@link AnnotationProvenance},
	 * while the variable parts are filled with the parameters in this method. 
	 * 
	 * @param propValue the {@link ExperimentalPropertyValue} text value used to compute this ontology annotation via ZOOMA 
	 * @param propType the {@link ExperimentalPropertyType} text value used etc etc
	 * @param score the confidence score that ZOOMA returns about the ontology result it found for this string pair
	 * @param timestamp when you computed this
	 */
	public static TextAnnotation createZOOMAMarker ( String propValue, String propType, Double score, Date timestamp )
	{
		TextAnnotation result = new TextAnnotation ( 
			new AnnotationType ( "Mapped from Text Values via ZOOMA" ),
			String.format ( "value: '%s', type: '%s'", propValue, propType ) 
		);
		
		result.setProvenance ( new AnnotationProvenance ( PropertyValAnnotationManager.PROVENANCE_MARKER ) );
		result.setScore ( score );
		result.setTimestamp ( timestamp );
		
		AnnotatorResources.getInstance ().getAnnNormalizer ().normalize ( result );
		
		return result;
	}
	
	/**
	 * Invokes {@link #createZOOMAMarker(String, String, Double, Date)} and creates an annotation with null score and
	 * null timestamp.
	 */
	public static TextAnnotation createZOOMAMarker ( String propValue, String propType )
	{
		return createZOOMAMarker ( propValue, propType, null, null );
	}

}
