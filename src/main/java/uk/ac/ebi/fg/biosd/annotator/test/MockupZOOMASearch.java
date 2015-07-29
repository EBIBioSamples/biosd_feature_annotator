package uk.ac.ebi.fg.biosd.annotator.test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import uk.ac.ebi.fgpt.zooma.model.Annotation;
import uk.ac.ebi.fgpt.zooma.model.AnnotationSummary;
import uk.ac.ebi.fgpt.zooma.model.Property;
import uk.ac.ebi.fgpt.zooma.model.SimpleAnnotationSummary;
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
	public Map<String, String> getPrefixMappings () throws IOException {
		return null;
	}

	@Override
	public Map<AnnotationSummary, Float> searchZOOMA ( 
		Property property, float score, boolean excludeType, boolean noEmptyResult 
	) 
	{
		try
		{
			Map<AnnotationSummary, Float> result = new HashMap<AnnotationSummary, Float> ();
			String pval = property.getPropertyValue ();
			String uri = lookup.get ( pval.toLowerCase () );
			
			if ( uri != null )
			{
				ArrayList<URI> uris = new ArrayList<URI> ();
				uris.add ( new URI ( uri ) );
				result.put ( 
					(AnnotationSummary) new SimpleAnnotationSummary ( "fooAnn1", "Specie", "Homo Sapiens", uris, null, 100.0f ),
					100.0f
				);
			}
			
			return result;
		}
		catch ( URISyntaxException ex )
		{
			throw new RuntimeException ( "Internal error: " + ex.getMessage (), ex );
		}
	}

	@Override
	public Annotation getAnnotation ( URI annotationURI ) {
		return null;
	}

	@Override
	public String getLabel ( URI uri ) throws IOException {
		return null;
	}
}