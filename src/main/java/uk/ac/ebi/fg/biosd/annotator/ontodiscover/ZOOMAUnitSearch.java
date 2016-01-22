package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import uk.ac.ebi.fg.biosd.annotator.persistence.AnnotatorAccessor;
import uk.ac.ebi.fgpt.zooma.model.AnnotationPrediction;
import uk.ac.ebi.fgpt.zooma.model.Property;
import uk.ac.ebi.fgpt.zooma.search.AbstractZOOMASearch;
import uk.ac.ebi.fgpt.zooma.search.ZOOMASearchFilter;

/**
 * It's like {@link AbstractZOOMASearch ZOOMA-based search}, but considers only terms coming from the 
 * <a href ="http://bioportal.bioontology.org/ontologies/UO">Unit Ontology</a>.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>17 Dec 2014</dd>
 *
 */
public class ZOOMAUnitSearch extends ZOOMASearchFilter
{
	public ZOOMAUnitSearch ( AbstractZOOMASearch base )
	{
		super ( base );
	}

	
	
	/**
	 * Returns the first (top-scored) result only. This is because we are currently considering that only in the 
	 * {@link AnnotatorAccessor}, due to the fact that this is usually the best we get and often the following results
	 * are not so good.  
	 * 
	 */	
	@Override
	public List<AnnotationPrediction> annotate ( Property property )
	{
		List<AnnotationPrediction> result = new ArrayList<> ();
		List<AnnotationPrediction> anns = super.annotate ( property );
		if ( anns == null || anns.isEmpty () ) return result;
		
		AnnotationPrediction bestResult = null;
		for ( AnnotationPrediction ann: anns )
		{
			Collection<URI> uris = ann.getSemanticTags ();
			if ( uris.size () != 1 ) continue;
			
			if ( bestResult == null || ann.getConfidence ().getScore () > bestResult.getConfidence ().getScore () )
				bestResult = ann;
		}
		result.add ( bestResult );
		return result;
	}
	
}
