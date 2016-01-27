package uk.ac.ebi.fg.biosd.annotator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.ebi.bioportal.webservice.client.BioportalClient;
import uk.ac.ebi.fgpt.zooma.model.AnnotationPrediction.Confidence;
import uk.ac.ebi.fgpt.zooma.search.AbstractZOOMASearch;
import uk.ac.ebi.fgpt.zooma.search.StatsZOOMASearchFilter;
import uk.ac.ebi.fgpt.zooma.search.ZOOMASearchClient;

import com.google.common.base.Supplier;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;

/**
 * Several common resources used by the annotator. 
 * TODO: We need Spring!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>18 Mar 2015</dd>
 *
 */
public class AnnotatorResources
{
	/**
	 * Property values or types longer than this length shouldn't be analysed
	 */
	public static final int MAX_STRING_LEN = 150;
	
	/**
	 * All the JAva properties for this application are prefixed with this.
	 */
	public static final String PROP_NAME_PREFIX = "uk.ac.ebi.fg.biosd.annotator.";
	
	
	/**
	 * Affects certain particularly slow functions (e.g., Unit Ontology fetching is skipped when this is tru).
	 */
	public static final String FAST_MODE_DEBUG_PROP_NAME = PROP_NAME_PREFIX + "debug.fast_mode";

	
	// This seems to be the only and absurd way to tell Google Collections that I want a damn efficiently-synchronised 
	// Table. ConcurrentHashMap is better than synchronising on the whole table object, synchronisation on 
	// single key values doesn't work.
	@SuppressWarnings ( "rawtypes" )
	private final Table<Class, String, Object> store = Tables.newCustomTable 
	( 
		new ConcurrentHashMap<Class, Map<String, Object>>(),
		new Supplier<Map<String, Object>>() 
		{
			@Override
			public Map<String, Object> get () {	return new ConcurrentHashMap<String, Object> ();	}
		}
	);
	
	private final AbstractZOOMASearch zoomaClient = new StatsZOOMASearchFilter ( new ZOOMASearchClient () );
	//private final AbstractZOOMASearch zoomaClient = new StatsZOOMASearchFilter ( new MockupZOOMASearch () );
	//private final AbstractZOOMASearch zoomaClient = new StatsZOOMASearchFilter ( new MockupFakeUrisZOOMASearch () );
	private final BioportalClient bioportalClient = new BioportalClient ( AnnotatorResources.BIOPORTAL_API_KEY );
  private final PropertyValAnnotationManager pvAnnMgr = new PropertyValAnnotationManager ( this );

	/**
	 * We use this here and in tests. Should you need it, please Get your own key from Bioportal, do not use this one.
	 */
	public final static String BIOPORTAL_API_KEY = "07732278-7854-4c4f-8af1-7a80a1ffc1bb";
	
	private static AnnotatorResources instance = new AnnotatorResources ();
		
	private AnnotatorResources () 
	{
		this.zoomaClient.setMinConfidence ( Confidence.GOOD );
	}

	public static AnnotatorResources getInstance ()
	{
		return instance;
	}
	
	/**
	 * Annotations computed by this tool are first saved in this in-memory double layer map. Later, they're saved on the 
	 * BioSD database. This ensures speed by avoiding too much DB synchronisation.
	 */
	@SuppressWarnings ( "rawtypes" )
	public Table<Class, String, Object> getStore ()
	{
		return store;
	}
	
	/**
	 * We use a common {@link PropertyValAnnotationManager} to run the feature annotator.
	 */
	public PropertyValAnnotationManager getPvAnnMgr ()
	{
		return pvAnnMgr;
	}
	
	/**
	 * The common ZOOMA client possibly used to perform ontology annotations over sample property values.
	 * The usage of this depends on {@link PropertyValAnnotationManager#ONTO_DISCOVERER_PROP_NAME}.
	 */
	public AbstractZOOMASearch getZoomaClient ()
	{
		return zoomaClient;
	}
	
	
	/**
	 * The common Bioportal client possibly used to perform ontology annotations over sample property values.
	 * The usage of this depends on {@link PropertyValAnnotationManager#ONTO_DISCOVERER_PROP_NAME}.
	 */
	public BioportalClient getBioportalClient ()
	{
		return bioportalClient;
	}

	/**
	 * This resets the common resources used by the annotator and provided by this singleton, the main one being
	 * {@link #getStore() annotation store}. This method is typically used at the end/begin of JUnit tests.  
	 */
	public void reset ()
	{
		this.store.clear ();
	}
}
