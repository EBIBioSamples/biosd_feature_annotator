package uk.ac.ebi.fg.biosd.annotator.persistence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.MemoryStore;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.Store;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.normalizers.toplevel.AnnotationNormalizer;
import uk.ac.ebi.fg.core_model.toplevel.Annotation;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer.DiscoveredTerm;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>18 Mar 2015</dd>
 *
 */
public class AnnotatorResources
{
	private final Store store = new SynchronizedStore ( new MemoryStore () );
	private final Map<String, List<DiscoveredTerm>> ontoTerms = new HashMap<String, List<DiscoveredTerm>> ();
	private final AnnotationNormalizer<Annotation> annNormalizer = new AnnotationNormalizer<Annotation> ( this.store );
	
	private static AnnotatorResources instance = new AnnotatorResources ();
	
	private AnnotatorResources () {}

	public static AnnotatorResources getInstance ()
	{
		return instance;
	}

	public Store getStore ()
	{
		return store;
	}

	public Map<String, List<DiscoveredTerm>> getOntoTerms ()
	{
		return ontoTerms;
	}

	public AnnotationNormalizer<Annotation> getAnnNormalizer ()
	{
		return annNormalizer;
	}
	
	public static final void reset ()
	{
		instance = new AnnotatorResources ();
	}
}
