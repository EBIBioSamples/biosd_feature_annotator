package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotationManager;
import uk.ac.ebi.fg.biosd.annotator.purge.Purger;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.DBStore;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.normalizers.terms.OntologyEntryNormalizer;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.normalizers.toplevel.AnnotationNormalizer;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.terms.OntologyEntryDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.AnnotationType;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
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
	
	/**
	 * Works as explained in the class comment.
	 * 
	 * Note that this method is synchronised, because we noticed DB lock problems when it wasn't. The speed isn't affected
	 * too much.
	 */
	@Override
	public synchronized List<DiscoveredTerm> save ( String valueLabel, String typeLabel, List<DiscoveredTerm> discoveredTerms ) 
		throws OntologyDiscoveryException
	{
		if ( discoveredTerms.isEmpty () )
		{
			// Save the special case where this entry isn't mapped to any term. We need to track this, so that we won't
			// re-discover it
			discoveredTerms = new ArrayList<> ();
			ExtendedDiscoveredTerm nullDt = new ExtendedDiscoveredTerm ( null, -1f, null );
			discoveredTerms.add ( nullDt );
		}
		
		// Now you have ontology term URIs to associate to this string pair, let's turn it all to BioSD model objects
		//
		Date now = new Date ();

		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
		EntityTransaction tx = em.getTransaction ();
		tx.begin ();

		OntologyEntryDAO<OntologyEntry> ontoDao = new OntologyEntryDAO<> ( OntologyEntry.class, em );
		OntologyEntryNormalizer oeNormalizer = new OntologyEntryNormalizer ( new DBStore ( em ) );
		AnnotationNormalizer<Annotation> annNormalizer = new AnnotationNormalizer<Annotation> ( new DBStore ( em ) );

		try
		{
			for ( int i = 0; i < discoveredTerms.size (); i++ )
			{
				DiscoveredTerm dterm = discoveredTerms.get ( i );
	
				// When it's already an extended discovered term, that's because the previous block here above has set the 
				// empty result special case
				//
				boolean isProperDiscovery = !(dterm instanceof ExtendedDiscoveredTerm);
				
				// If it's not real, mark that this entries has no mapping, ie, cache this too
				String dtermUri = isProperDiscovery ? dterm.getUri ().toASCIIString () : NULL_TERM_URI;
				
				
				// Try to map it to an existing ontology entry, or to create a new one
				OntologyEntry otermDb = ontoDao.find ( dtermUri, null, null, null );
	
				OntologyEntry oterm = otermDb == null 
						// Create the ontology term that represents this mapping
					? new OntologyEntry ( dtermUri, null )
						// Reuse the existing term
				  : otermDb;
					
				// Annotate the origin of this mapping
				float dscore = dterm.getScore ();
				Double savedScore = dscore == -1 ? null : (double) dscore; // as above, it's null when it's mapped to a null term
				Annotation zoomaMarker = createZOOMAMarker ( valueLabel, typeLabel, savedScore, now );
				
				// This annotation is certainly new, due to the way the cache works, however the type and provenance objects
				// are likely to be normalised.
				// Normally the annotation normaliser is triggered by the ontology normaliser, however this might not happen here,
				// cause the ontology entry might already exist (ie, a new annotation about a new string pair is being added) 
				//
				annNormalizer.normalize ( zoomaMarker );
				oterm.addAnnotation ( zoomaMarker ); // Typically it doesn't do anything, but just in case.
				
				// Almost done, save it all to the DB
				oeNormalizer.normalize ( oterm );
				if ( oterm.getId () == null ) ontoDao.create ( oterm ); else em.merge ( oterm );
			
				if ( isProperDiscovery)
				{
					// Replace the current discovered term with a new one having the ontology term attached, which is 
					// needed by the annotator tool to link property values
					ExtendedDiscoveredTerm dtermNew = new ExtendedDiscoveredTerm ( dterm.getUri (), dscore, oterm );
					discoveredTerms.set ( i, dtermNew );
				}
				else
				{
					// Return the empty result, the annotator will need to deal with such a case
					// We can stop the for loop here, because we're sure there is only this null element in discoveredTerms
					tx.commit (); // close the current transaction before
					return CachedOntoTermDiscoverer.NULL_RESULT;
				}
			} // for discoveredTerms
		
			tx.commit ();
			return discoveredTerms;
		}
		finally {
			if ( em.isOpen () ) em.close ();
		} 
	}

	/**
	 * Works as explained in the class comment.
	 */
	@Override
	@SuppressWarnings ( "unchecked" )
	public List<DiscoveredTerm> getOntologyTermUris ( String valueLabel, String typeLabel ) throws OntologyDiscoveryException
	{
		// Search if there are ontology terms to which this textual entry is mapped
		// 
		TextAnnotation zoomaMarker = createZOOMAMarker ( valueLabel, typeLabel );
		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();

		try
		{
			// Hibernate protects against concurrency issues by wrapping readings into transactions too.
			// Ridiculous...
			EntityTransaction tx = em.getTransaction ();
			tx.begin ();
		
			List<Object[]> dbentries =  (List<Object[]>) em.createNamedQuery ( "findOntoAnnotations" )
			  .setParameter ( "provenance", zoomaMarker.getProvenance ().getName () )
			  .setParameter ( "annotation", zoomaMarker.getText () )
			  .setHint ( "org.hibernate.readOnly", true )
				.getResultList ();
			tx.commit ();
			
			// The text entry doesn't exist at all: we don't have it yet and therefore we have to report null
			if ( dbentries.isEmpty () ) return null;

			// Now turn the real result into the required return format
			List<DiscoveredTerm> result = new ArrayList<> ();

			for ( Object[] tuple: dbentries )
			{
				OntologyEntry oterm = (OntologyEntry) tuple [ 0 ];
				Double score = (Double) tuple [ 1 ];
				
				if ( NULL_TERM_URI.equals ( oterm.getAcc () ) )
					// This entry is reported to map to an empty result, so return the corresponding value (an empty list)
					return CachedOntoTermDiscoverer.NULL_RESULT;
				
				// else return the discovered term that correspond to this entry
				ExtendedDiscoveredTerm dterm = new ExtendedDiscoveredTerm ( 
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
