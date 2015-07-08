package uk.ac.ebi.fg.biosd.annotator.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.Index;

import uk.ac.ebi.fg.core_model.terms.OntologyEntry;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>5 May 2015</dd>
 * 
 */
@Entity
@Table ( name = "onto_entry_computed" )
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
	@Column ( length = 2000 )
	public String getUri ()
	{
		return uri;
	}
	
	public void setUri ( String uri )
	{
		this.uri = uri;
	}
	
	@Index ( name = "oe_computed_lbl" )
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
