package uk.ac.ebi.fg.biosd.annotator.model;

import static uk.ac.ebi.fg.biosd.annotator.resources.AnnotatorBioSDResources.TABLE_PREFIX;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;

import uk.ac.ebi.fg.core_model.resources.Const;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;

/**
 * This is can be used to associate information to an ontology term URI, usually stored by 
 * {@link AbstractOntoTermAnnotation}. At the moment, only the {@link OntoResolverAndAnnotator} gets term labels
 * and link this class to {@link ResolvedOntoTermAnnotation}.
 * 
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>5 May 2015</dd>
 * 
 */
@Entity
@Table ( 
	name = TABLE_PREFIX + "oe_computed", 
	indexes = @Index ( columnList = "label" )
)
public class ComputedOntoTerm
{
	private String uri;
	private String label;
	
	protected ComputedOntoTerm () {}

	public ComputedOntoTerm ( String uri, String label )
	{
		this.uri = uri;
		this.label = label;
	}
	
	public ComputedOntoTerm ( String uri )
	{
		this ( uri, null );
	}


	@Id
	@Column ( length = Const.COL_LENGTH_URIS )
	public String getUri ()
	{
		return uri;
	}
	
	public void setUri ( String uri )
	{
		this.uri = uri;
	}
	
	@Column ( length = Const.COL_LENGTH_L )
	public String getLabel ()
	{
		return label;
	}
	public void setLabel ( String label )
	{
		this.label = label;
	}
	
	public OntologyEntry asOntologyEntry ()
	{
		OntologyEntry result = new OntologyEntry ( this.getUri (), null );
		result.setLabel ( this.getLabel () );
		return result;
	}

	@Override
	public int hashCode ()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ( ( uri == null ) ? 0 : uri.hashCode () );
		return result;
	}

	@Override
	public boolean equals ( Object obj )
	{
		if ( this == obj ) return true;
		if ( obj == null ) return false;
		if ( getClass () != obj.getClass () ) return false;
		
		ComputedOntoTerm other = (ComputedOntoTerm) obj;
		if ( uri == null ) {
			if ( other.uri != null ) return false;
		} 
		else if ( !uri.equals ( other.uri ) ) return false;
		return true;
	}
	
}
