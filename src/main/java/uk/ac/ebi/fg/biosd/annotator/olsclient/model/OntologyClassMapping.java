package uk.ac.ebi.fg.biosd.annotator.olsclient.model;

/**
 * This reflects the results returned by the 
 * <a href = 'http://data.bioontology.org/documentation#Mapping'>mapping service</a>.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>13 Jan 2016</dd></dl>
 *
 */
public class OntologyClassMapping
{
	private String id;
	private String source;
	private String process;
	private ClassRef targetClassRef;

	public OntologyClassMapping () {
		super ();
	}

	public OntologyClassMapping (
		String id, String source, String process, OntologyClass leftClass, OntologyClass rightClass
	)
	{
		super ();
		this.id = id;
		this.source = source;
		this.process = process;
	}

	public String getId ()
	{
		return id;
	}

	public void setId ( String id )
	{
		this.id = id;
	}

	public String getSource ()
	{
		return source;
	}

	public void setSource ( String source )
	{
		this.source = source;
	}

	public String getProcess ()
	{
		return process;
	}

	public void setProcess ( String process )
	{
		this.process = process;
	}

	/**
	 * The mapping API returns a pair of classes for each mapping, but the first is always the input class, so we
	 * can just omit it.
	 */
	public ClassRef getTargetClassRef ()
	{
		return targetClassRef;
	}

	public void setTargetClassRef ( ClassRef targetClassRef )
	{
		this.targetClassRef = targetClassRef;
	}

	@Override
	public String toString ()
	{ 
		return String.format ( "%s { id: %s, targetClassRef: %s, source: %s, process: %s }", 
			this.getClass ().getSimpleName (), 
			this.getId (), 
			this.getTargetClassRef (),
		  this.getSource (),
		  this.getProcess ()
		);
	}

}
