package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.DBStore;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.normalizers.toplevel.AnnotatableNormalizer;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.terms.OntologyEntryDAO;
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
 * TODO: Comment me!
 *
 * <dl><dt>date</dt><dd>1 Aug 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class BioSDOntoDiscoveringCache extends OntoTermDiscoveryCache
{
	public final static String NULL_TERM_ACC = "__NULL TERM__";
			
	@Override
	public List<DiscoveredTerm> save ( String valueLabel, String typeLabel, List<DiscoveredTerm> discoveredTerms ) throws OntologyDiscoveryException
	{
		if ( discoveredTerms.isEmpty () )
		{
			// Save the special case where this entry isn't mapped to any term. We need to mark this, so that we won't
			// re-discover it
			ExtendedDiscoveredTerm nullDt = new ExtendedDiscoveredTerm ( null, -1f, null );
			discoveredTerms.add ( nullDt );
		}

		
		// Now you have ontology term URIs to associate to this pair, let's turn it all to BioSD model objects
		//
		Date now = new Date ();
		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();

		OntologyEntryDAO<OntologyEntry> ontoDao = new OntologyEntryDAO<> ( OntologyEntry.class, em );
		AnnotatableNormalizer<Annotatable> annotatableNormalizer = new AnnotatableNormalizer<> ( new DBStore ( em ) );

		
		for ( int i = 0; i < discoveredTerms.size (); i++ )
		{
			DiscoveredTerm dterm = discoveredTerms.get ( i );

			// When it's an extended discovered term, that's because the previous block here above has set the 
			// empty result special case
			//
			boolean isProperDiscovery = !(dterm instanceof ExtendedDiscoveredTerm);
			
			OntologyEntry oterm;
			if ( isProperDiscovery )
			{
				// So, if this URI is real, try to map it to an existing ontology entry, or to create a new one
				//
				String dtermUri = dterm.getUri ().toASCIIString ();
				List<OntologyEntry> oterms = ontoDao.find ( dtermUri, null );
				
				oterm = oterms.isEmpty () 
						// Create the ontology term that represents this mapping
					? new OntologyEntry ( dterm.getUri ().toASCIIString (), null )
						// Reuse the existing term
				  : oterms.iterator ().next ();
			}
			else
				// dterm is a fake entry, which should generate the mapping to the 'null' ontology entry + NULL_RESULT 
				oterm = new OntologyEntry ( NULL_TERM_ACC, null );
				
			// Annotate the origin of this mapping
			float dscore = dterm.getScore ();
			Double savedScore = dscore == -1 ? null : (double) dscore;
			Annotation zoomaMarker = createZOOMAMarker ( valueLabel, typeLabel, savedScore, now );
			oterm.addAnnotation ( zoomaMarker );
			
			// Save it all to the DB
			annotatableNormalizer.normalize ( oterm );
			EntityTransaction tx = em.getTransaction ();
			tx.begin ();
			if ( oterm.getId () == null ) ontoDao.create ( oterm ); else em.merge ( oterm );
			tx.commit ();
			
			if ( isProperDiscovery)
			{
				// Replace the current discovered term with a new one having the ontology term attached, which is 
				// needed by the annotator tool to link property values
				ExtendedDiscoveredTerm dtermNew = new ExtendedDiscoveredTerm ( dterm.getUri (), dscore, oterm );
				discoveredTerms.set ( i, dtermNew );
			}
			else
				// Return the empty result, the annotator will need to deal with such a case
				return CachedOntoTermDiscoverer.NULL_RESULT; 
		}
		
		return discoveredTerms;
	}

	@Override
	@SuppressWarnings ( "unchecked" )
	public List<DiscoveredTerm> getOntologyTermUris ( String valueLabel, String typeLabel ) throws OntologyDiscoveryException
	{
		try
		{
			// Search if there are ontology terms to which this textual entry is mapped
			// 
			TextAnnotation zoomaMarker = createZOOMAMarker ( valueLabel, typeLabel );
			EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();

			
			// Turn the result into appropriate format
			List<DiscoveredTerm> result = new ArrayList<> ();
			List<Object[]> dbentries =  (List<Object[]>) em.createNamedQuery ( "findOntoAnnotations" )
			  .setParameter ( "provenance", zoomaMarker.getProvenance ().getName () )
			  .setParameter ( "annotation", zoomaMarker.getText () )
				.getResultList ();
			
			// The text entry doesn't exist at all: we don't have it yet and therefore we have to report null
			if ( dbentries.isEmpty () ) return null;
			
			for ( Object[] tuple: dbentries )
			{
				OntologyEntry oterm = (OntologyEntry) tuple [ 0 ];
				Double score = (Double) tuple [ 1 ];
				
				if ( NULL_TERM_ACC.equals ( oterm ) )
					// This entry is reported to map to an empty result, so return the corresponding value
					return CachedOntoTermDiscoverer.NULL_RESULT;
				
				// return the discovered term that correspond to this entry
				ExtendedDiscoveredTerm dterm = new ExtendedDiscoveredTerm ( 
					new URI ( oterm.getAcc () ),  score.floatValue (), oterm 
				);
				result.add ( dterm );
			}
			
			return result;
		} 
		catch ( URISyntaxException ex )
		{
			throw new OntologyDiscoveryException (
				String.format ( 
					"Error while fetching ZOOMA annotation from the BioSD DB for '%s'/'%s': %s", 
					valueLabel, typeLabel, ex.getMessage () 
				),
				ex 
			);
		}
	}

	
	public static TextAnnotation createZOOMAMarker ( String provValue, String provType, Double score, Date timestamp )
	{
		TextAnnotation result = new TextAnnotation ( 
			new AnnotationType ( "Mapped from Text Values" ),
			String.format ( "value: '%s', type: '%s'", provValue, provType ) 
		);
		
		result.setProvenance ( new AnnotationProvenance ( "BioSD Feature Annotation Tool, based on ZOOMA" ) );
		result.setScore ( score );
		result.setTimestamp ( timestamp );
		
		return result;
	}
	
	public static TextAnnotation createZOOMAMarker ( String provValue, String provType )
	{
		return createZOOMAMarker ( provValue, provType, null, null );
	}

	
	
}
