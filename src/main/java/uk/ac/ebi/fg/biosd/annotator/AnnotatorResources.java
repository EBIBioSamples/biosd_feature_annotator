package uk.ac.ebi.fg.biosd.annotator;

import uk.ac.ebi.fgpt.zooma.search.AbstractZOOMASearch;
import uk.ac.ebi.fgpt.zooma.search.StatsZOOMASearchFilter;
import uk.ac.ebi.fgpt.zooma.search.ZOOMASearchClient;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

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
	
	// TODO: needs to be moved to 'store'
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
	
	public Table<Class, String, Object> getStore ()
	{
		return store;
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
