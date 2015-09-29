package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;

import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.fg.biosd.annotator.persistence.dao.ExpPropValAnnotationDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.onto_discovery.api.CachedOntoTermDiscoverer;
import uk.ac.ebi.onto_discovery.api.OntoTermDiscoveryCache;
import uk.ac.ebi.onto_discovery.api.OntologyDiscoveryException;

/**
 * {@link OntoTermDiscoveryCache} that checks the existence of ontology term annotations inside the BioSD database, 
 * using {@link ExpPropValAnnotationDAO}.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>25 Jun 2015</dd>
 *
 */
public class BioSDOntoDiscoveringCache extends OntoTermDiscoveryCache
{	
	@Override
	public List<DiscoveredTerm> save ( String valueLabel, String typeLabel, List<DiscoveredTerm> dterms )
		throws OntologyDiscoveryException
	{
		return dterms;
	}

	

	@Override
	public List<DiscoveredTerm> getOntologyTerms ( String valueLabel, String typeLabel ) throws OntologyDiscoveryException
	{
		String pvkey = ExpPropValAnnotation.getPvalText ( typeLabel, valueLabel );
		if ( pvkey == null ) return CachedOntoTermDiscoverer.NULL_RESULT;

		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
		ExpPropValAnnotationDAO expPropValAnnotationDAO = new ExpPropValAnnotationDAO ( em );

		try
		{
			List<ExpPropValAnnotation> pvanns = expPropValAnnotationDAO.findBySourceText ( pvkey, true );
			
			if ( pvanns == null || pvanns.isEmpty () ) return null;
			if ( pvanns.size () == 1 
					&& ExpPropValAnnotation.NULL_TERM_URI.equals ( pvanns.iterator ().next ().getOntoTermUri () ) 
			) 
				// An annotation with null URI is the way to say I already know this pv was mapped to nothing
				return CachedOntoTermDiscoverer.NULL_RESULT;
			
			List<DiscoveredTerm> result = new ArrayList<DiscoveredTerm> ();
			for ( ExpPropValAnnotation ann: pvanns )
				result.add ( new DiscoveredTerm ( ann.getOntoTermUri (), ann.getScore ().floatValue () ) );
			
			return result;
		}
		finally {
			if ( em.isOpen () ) em.close ();
		}
	}
	
}
