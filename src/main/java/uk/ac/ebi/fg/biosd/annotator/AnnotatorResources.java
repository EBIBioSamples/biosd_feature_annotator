package uk.ac.ebi.fg.biosd.annotator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
	 * Property values or types longer than this lenght shouldn't be analysed
	 */
	public static final int MAX_STRING_LEN = 150;
	
	@SuppressWarnings ( "rawtypes" )
	// This seems to be the only and absurd way to tell Google Collections that I want a damn efficiently-synchronised 
	// Table. ConcurrentHashMap is better than synchronising on the whole table object, synchronisation on 
	// single key values doesn't work.
	private final Table<Class, String, Object> store = Tables.newCustomTable 
	( 
		new ConcurrentHashMap<Class, Map<String, Object>>(),
		new Supplier<Map<String, Object>>() 
		{
			@Override
			public Map<String, Object> get () {	return new ConcurrentHashMap<String, Object> ();	}
		}
	);
	
	
	// HashBasedTable.create ();	
	
	private final AbstractZOOMASearch zoomaClient = new StatsZOOMASearchFilter ( new ZOOMASearchClient () );
	//private final AbstractZOOMASearch zoomaClient = new StatsZOOMASearchFilter ( new MockupZOOMASearch () );

	private final PropertyValAnnotationManager pvAnnMgr;
	
	private static AnnotatorResources instance = new AnnotatorResources ();
		
	private AnnotatorResources () 
	{
		((StatsZOOMASearchFilter) this.zoomaClient).setThrottleMode ( true );
		pvAnnMgr = new PropertyValAnnotationManager ( this.zoomaClient );
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
	 * The common ZOOMA client used to perform ontology annotations over sample property values.
	 */
	public AbstractZOOMASearch getZoomaClient ()
	{
		return zoomaClient;
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
