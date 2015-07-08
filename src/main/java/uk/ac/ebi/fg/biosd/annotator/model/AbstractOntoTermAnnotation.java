package uk.ac.ebi.fg.biosd.annotator.model;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;


/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>25 Jun 2015</dd>
 *
 */
@MappedSuperclass
public abstract class AbstractOntoTermAnnotation extends FeatureAnnotation
{

	private String ontoTermUri;
	
	/**
	 * Used to say that an entity hasn't any ontology term associated.
	 */
	public final static String NULL_TERM_URI = "http://rdf.ebi.ac.uk/terms/biosd/NullOntologyTerm";

	protected AbstractOntoTermAnnotation ()
	{
		super ();
	}

	/**
	 * @param sourceText
	 */
	public AbstractOntoTermAnnotation ( String sourceText )
	{
		super ( sourceText );
	}

	@Column ( length = 2000, name = "term_uri" )
	public String getOntoTermUri ()
	{
		return ontoTermUri;
	}

	public void setOntoTermUri ( String ontoTermUri )
	{
		this.ontoTermUri = ontoTermUri;
	}

}