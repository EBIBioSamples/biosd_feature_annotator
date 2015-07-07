package uk.ac.ebi.fg.biosd.annotator.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.NamedQuery;

import org.hibernate.annotations.Index;
import org.hibernate.annotations.Type;

/**
 * TODO: Comment me!
 *
 * <dl><dt>date</dt><dd>23 Jun 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
@Entity
@DiscriminatorValue ( "date_range" )
@NamedQuery ( 
	name = "dateRangeItem.find", 
	query = "FROM DateRangeItem d WHERE " 
		+ "( :low IS NULL AND d.low IS NULL OR d.low = :low ) AND ( :hi IS NULL AND d.hi IS NULL OR d.hi = :hi )" 
)
public class DateRangeItem extends RangeItem<Date>
{
	protected DateRangeItem () {
		super ();
	}

	public DateRangeItem ( Date low, Date hi ) {
		super ( low, hi );
	}
	
	public DateRangeItem ( Date low, Date hi, String sourceText ) {
		super ( low, hi, sourceText );
	}

	
	@Column ( name = "date_low" )
	@Index ( name = "date_range_lo" )
	@Override
	public Date getLow ()
	{
		return super.getLow ();
	}

	@Column ( name = "date_hi" )
	@Index ( name = "date_range_hi" )
	@Override
	public Date getHi ()
	{
		return super.getHi ();
	}
}
