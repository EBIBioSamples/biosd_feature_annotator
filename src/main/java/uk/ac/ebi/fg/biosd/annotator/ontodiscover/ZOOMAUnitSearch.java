package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import java.net.URI;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import uk.ac.ebi.fgpt.zooma.model.AnnotationSummary;
import uk.ac.ebi.fgpt.zooma.model.Property;
import uk.ac.ebi.fgpt.zooma.search.ZOOMASearchFilter;
import uk.ac.ebi.fgpt.zooma.search.ZOOMASearchInterface;

/**
 * It's like {@link ZOOMASearchInterface ZOOMA-based search}, but considers only terms coming from the 
 * <a href ="http://bioportal.bioontology.org/ontologies/UO Unit">Ontology</a>.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>17 Dec 2014</dd>
 *
 */
public class ZOOMAUnitSearch extends ZOOMASearchFilter
{
	public ZOOMAUnitSearch ( ZOOMASearchInterface base )
	{
		super ( base );
	}

	@Override
	public Map<AnnotationSummary, Float> searchZOOMA ( Property property, float score, boolean excludeType, boolean noEmptyResult )
	{
		Map<AnnotationSummary, Float> result = super.searchZOOMA ( property, score, excludeType, noEmptyResult );
		for ( Iterator<AnnotationSummary> sumItr = result.keySet ().iterator (); sumItr.hasNext (); )
		{
			AnnotationSummary sum = sumItr.next ();
						
			Collection<URI> semTags = sum.getSemanticTags ();
			if ( semTags == null ) {
				sumItr.remove ();
				continue;
			}
			
			for ( URI uri: semTags )
				if ( !uri.toASCIIString ().startsWith ( "http://purl.obolibrary.org/obo/UO_" ) ) {
					sumItr.remove (); 
					break;
			}
					
		}

		return result;
	}
	
}
