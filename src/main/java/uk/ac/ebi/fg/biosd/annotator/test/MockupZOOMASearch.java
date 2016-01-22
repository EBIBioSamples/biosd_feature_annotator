package uk.ac.ebi.fg.biosd.annotator.test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.ac.ebi.fgpt.zooma.model.AnnotationPrediction;
import uk.ac.ebi.fgpt.zooma.model.Property;
import uk.ac.ebi.fgpt.zooma.model.SimpleAnnotationPrediction;
import uk.ac.ebi.fgpt.zooma.search.AbstractZOOMASearch;

/**
 * Returns constant URIs to predefined labels. I use this to test when I don't have Internet. 
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>26 Mar 2015</dd>
 *
 */
public class MockupZOOMASearch extends AbstractZOOMASearch
{
	Map<String, String> lookup = new HashMap<String, String> ()
	{{
		put ( "homo sapiens", "http://purl.obolibrary.org/obo/NCBITaxon_9606" );
		put ( "kg", "http://purl.obolibrary.org/obo/UO_0000009" );
		put ( "mus musculus", "http://purl.obolibrary.org/obo/NCBITaxon_10090" );
	}};
	
	
	@Override
	public List<AnnotationPrediction> annotate ( Property property )
	{
		try
		{
			List<AnnotationPrediction> result = new ArrayList<> ();
			String pval = property.getPropertyValue ();
			String uri = lookup.get ( pval.toLowerCase () );
			
			if ( uri != null ) result.add ( new SimpleAnnotationPrediction ( 
				null, AnnotationPrediction.Confidence.HIGH, null, property, null, new URI [] { new URI ( uri ) } 
			)); 
			
			return result;
		}
		catch ( URISyntaxException ex ) {
			throw new RuntimeException ( "Internal error: " + ex.getMessage (), ex );
		}		
	}


}