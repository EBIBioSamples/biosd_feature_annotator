package uk.ac.ebi.fg.biosd.annotator;

import uk.ac.ebi.fgpt.zooma.search.AbstractZOOMASearch;
import uk.ac.ebi.fgpt.zooma.search.StatsZOOMASearchFilter;
import uk.ac.ebi.fgpt.zooma.search.ZOOMASearchClient;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

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
	private final Table<Class, String, Object> store = HashBasedTable.create ();	
	
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
	 * {@link #getStore() annotation store}. This methos is typically used at the end/begin of JUnit tests.  
	 */
	public static final void reset ()
	{
		instance = new AnnotatorResources ();
	}
}
