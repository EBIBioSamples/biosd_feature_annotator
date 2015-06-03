package uk.ac.ebi.fg.biosd.annotator.model;

import java.util.Date;

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
@DiscriminatorValue ( "date" )
@NamedQuery ( 
	name = "dateItem.find", 
	query = "FROM DateItem d WHERE d.value = :value" 
)
public class DateItem extends ValueItem<Date>
{
	protected DateItem () {
		super ();
	}

	public DateItem ( Date value ) {
		super ( value );
	}

	public DateItem ( Date value, String sourceText ) {
		super ( value, sourceText );
	}

	
	@Column ( name = "date_val" )
	@Index ( name = "date_item_value" )
	@Override
	public Date getValue ()
	{
		return super.getValue ();
	}

}
