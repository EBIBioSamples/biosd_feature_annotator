package uk.ac.ebi.fg.biosd.annotator.olsclient.model;

/**
 * Essentially, this is a URI (or accession) plus an acronym, which is the way Bioportal get ontology class references
 * when needed.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>13 Jan 2016</dd></dl>
 *
 */
public class ClassRef 
{
	private String classIri;
	private String ontologyAcronym;
	
	public String getClassIri () {
		return classIri;
	}
	
	public void setClassIri ( String classIri ) {
		this.classIri = classIri;
	}
	
	public String getOntologyAcronym () {
		return ontologyAcronym;
	}
	
	public void setOntologyAcronym ( String ontologyAcronym ) {
		this.ontologyAcronym = ontologyAcronym;
	}

	
	public ClassRef ( String classIri, String ontologyAcronym )
	{
		this.classIri = classIri;
		this.ontologyAcronym = ontologyAcronym;
	}
	
	@Override
	public String toString ()
	{
		return String.format ( 
			"%s { classIri: <%s>, ontologyAcronym: '%s' }", 
			this.getClass ().getSimpleName (), this.getClassIri (), this.getOntologyAcronym ()
		);
	}
	
}