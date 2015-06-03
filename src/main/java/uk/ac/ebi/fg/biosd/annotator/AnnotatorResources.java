package uk.ac.ebi.fg.biosd.annotator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.ac.ebi.fg.biosd.annotator.persistence.SynchronizedStore;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.MemoryStore;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.Store;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.normalizers.toplevel.AnnotationNormalizer;
import uk.ac.ebi.fg.core_model.toplevel.Annotation;
import uk.ac.ebi.fgpt.zooma.search.AbstractZOOMASearch;
import uk.ac.ebi.fgpt.zooma.search.StatsZOOMASearchFilter;
import uk.ac.ebi.fgpt.zooma.search.ZOOMASearchClient;
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
	/**
	 * Property values or types longer than this lenght shouldn't be analysed
	 */
	public static final int MAX_STRING_LEN = 150;
	
	private final Store store = new SynchronizedStore ( new MemoryStore () );
	private final Map<String, List<DiscoveredTerm>> ontoTerms = new HashMap<String, List<DiscoveredTerm>> ();
	private final AnnotationNormalizer<Annotation> annNormalizer = new AnnotationNormalizer<Annotation> ( this.store );
	private final AbstractZOOMASearch zoomaClient = new StatsZOOMASearchFilter ( new ZOOMASearchClient () );
	private final PropertyValAnnotationManager pvAnnMgr;
	
	private static AnnotatorResources instance = new AnnotatorResources ();
	
	private AnnotatorResources () 
	{
		((StatsZOOMASearchFilter) this.zoomaClient).setThrottleMode ( true );
		pvAnnMgr = new PropertyValAnnotationManager ( this );
	}

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
	
	public PropertyValAnnotationManager getPvAnnMgr ()
	{
		return pvAnnMgr;
	}
	
	public AbstractZOOMASearch getZoomaClient ()
	{
		return zoomaClient;
	}

	
	public static final void reset ()
	{
		instance = new AnnotatorResources ();
	}
}
