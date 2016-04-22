package uk.ac.ebi.fg.biosd.annotator.olsclient.model;

/**
 * A simple model of an ontology, as it is represented by the Bioportal web service.
 *
 * <dl><dt>date</dt><dd>1 Oct 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class Ontology
{
	private String acronym;
	private String name;
	private String classUriPrefix;
	
	
	public Ontology ()
	{
		super ();
	}


	public Ontology ( String acronym )
	{
		super ();
		this.acronym = acronym;
	}


	public String getAcronym ()
	{
		return acronym;
	}


	public void setAcronym ( String acronym )
	{
		this.acronym = acronym;
	}


	public String getName ()
	{
		return name;
	}

	/**
	 * A human-readable title
	 */
	public void setName ( String name )
	{
		this.name = name;
	}

	/**
	 * This is what it can be used to build the full URI of a class belonging to this ontology, by attaching the class
	 * accession (ie, something like EFO_0000270).
	 * 
	 * It doesn't always exists or achievable from Bioportal, it's null when it's not available (you've to know the class
	 * URIs if that is the case). 
	 */
	public String getClassUriPrefix ()
	{
		return classUriPrefix;
	}


	public void setClassUriPrefix ( String classUriPrefix )
	{
		this.classUriPrefix = classUriPrefix;
	}
	
	@Override
	public String toString ()
	{
		return String.format ( 
			"%s { aconym: '%s', name: '%s', classUriPrefix <%s> }", 
			this.getClass ().getSimpleName (), this.getAcronym (), this.getName (), this.getClassUriPrefix ()
		);
	}
	
}
