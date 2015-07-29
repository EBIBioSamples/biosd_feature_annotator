package uk.ac.ebi.fg.biosd.annotator.model;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;

import org.apache.commons.lang3.StringUtils;


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

	
  @Override
  public boolean equals ( Object o ) 
  {
  	if ( o == null ) return false;
  	if ( this == o ) return true;
  	if ( this.getClass () != o.getClass () ) return false;
  	
  	AbstractOntoTermAnnotation that = (AbstractOntoTermAnnotation) o;
    
  	String sourceText = this.getSourceText ();
    if ( sourceText != null ? !sourceText.equals ( that.getSourceText () ) : that.getSourceText () != null )
    	return false;
    
    return this.getOntoTermUri () != null 
    	? this.ontoTermUri.equals ( that.getOntoTermUri () )
    	: that.getOntoTermUri () == null;
  }
  
  @Override
  public int hashCode() 
  {
  	String sourceText = this.getSourceText ();
  	int result = sourceText == null ? 0 : sourceText.hashCode ();
  	return 31 * result + ( this.getOntoTermUri () == null ? 0 : this.getOntoTermUri ().hashCode () ); 
  }

	@Override
	public String toString ()
	{
  	return String.format ( 
  		" %s { sourceString: %s, ontoTermUri: %s, type: %s, timestamp: %tc, provenance: %s, score: %f, notes: '%s', internalNotes: '%s' }", 
  		this.getClass ().getSimpleName (), this.getSourceText (), this.getOntoTermUri (), this.getType (),
  		this.getTimestamp (), this.getProvenance (), this.getScore (), StringUtils.abbreviate ( this.getNotes (), 20 ), 
  		StringUtils.abbreviate ( this.getInternalNotes (), 20 )
  	);
	}  		
		
}