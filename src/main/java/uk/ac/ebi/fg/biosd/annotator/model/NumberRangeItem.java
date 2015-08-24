package uk.ac.ebi.fg.biosd.annotator.model;


import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Index;

/**
 * TODO: Comment me!
 *
 * <dl><dt>date</dt><dd>23 Jun 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
@Entity
@DiscriminatorValue ( "number_range" )
@NamedQuery ( 
	name = "numberRangeItem.find", 
	query = "FROM NumberRangeItem d WHERE " 
		+ "( :low IS NULL AND d.low IS NULL OR d.low = :low ) AND ( :hi IS NULL AND d.hi IS NULL OR d.hi = :hi )" 
)
public class NumberRangeItem extends RangeItem<Double>
{
	protected NumberRangeItem () {
		super ();
	}

	public NumberRangeItem ( Double low, Double hi ) {
		super ( low, hi );
	}
	
	public NumberRangeItem ( Double low, Double hi, String sourceText ) {
		super ( low, hi, sourceText );
	}

	
	@Column ( name = "number_low" )
	@Override
	public Double getLow ()
	{
		return super.getLow ();
	}

	@Column ( name = "number_hi" )
	@Override
	public Double getHi ()
	{
		return super.getHi ();
	}

}
