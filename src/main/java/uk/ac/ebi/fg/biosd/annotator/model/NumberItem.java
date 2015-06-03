package uk.ac.ebi.fg.biosd.annotator.model;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.NamedQuery;

import org.hibernate.annotations.Index;

/**
 * TODO: Comment me!
 *
 * <dl><dt>date</dt><dd>23 Jun 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
@Entity
@DiscriminatorValue ( "number" )
@NamedQuery ( 
	name = "numberItem.find", 
	query = "FROM NumberItem d WHERE d.value = :value" 
)
public class NumberItem extends ValueItem<Double>
{

	protected NumberItem () {
		super ();
	}

	public NumberItem ( Double value ) {
		super ( value );
	}
	
	public NumberItem ( Double value, String sourceText ) {
		super ( value, sourceText );
	}

	@Column ( name = "number_val" )
	@Index ( name = "number_item_value" )
	@Override
	public Double getValue ()
	{
		return super.getValue ();
	}
}
