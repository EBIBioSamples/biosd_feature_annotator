package uk.ac.ebi.fg.biosd.annotator.test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;

import uk.ac.ebi.fgpt.zooma.model.AnnotationPrediction;
import uk.ac.ebi.fgpt.zooma.model.Property;
import uk.ac.ebi.fgpt.zooma.model.SimpleAnnotationPrediction;
import uk.ac.ebi.fgpt.zooma.model.TypedProperty;
import uk.ac.ebi.fgpt.zooma.search.AbstractZOOMASearch;

/**
 * 
 * A stupid {@link AbstractZOOMASearch ZOOMA client}, which returns a fake URI, built using the textual input.
 * Used for tests.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>26 Jan 2016</dd></dl>
 *
 */
public class MockupFakeUrisZOOMASearch extends AbstractZOOMASearch
{
	
	@Override
	public List<AnnotationPrediction> annotate ( Property property )
	{
		try
		{
			List<AnnotationPrediction> result = new ArrayList<> ();
			String pval = property.getPropertyValue ();
			String ptype = property instanceof TypedProperty ? ((TypedProperty) property).getPropertyType () : "";
			String uri = "http://www.somewhere.net/foo/term#" + DigestUtils.md5Hex ( ptype + pval );
			
			result.add ( new SimpleAnnotationPrediction ( 
				null, AnnotationPrediction.Confidence.HIGH, null, property, null, new URI [] { new URI ( uri ) } 
			)); 
			
			return result;
		}
		catch ( URISyntaxException ex ) {
			throw new RuntimeException ( "Internal error: " + ex.getMessage (), ex );
		}		
	}


}