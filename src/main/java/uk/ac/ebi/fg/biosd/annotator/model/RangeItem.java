package uk.ac.ebi.fg.biosd.annotator.model;

import javax.persistence.Entity;
import javax.persistence.Transient;



/**
 * TODO: Comment me!
 *
 * <dl><dt>date</dt><dd>23 Jun 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
@Entity
//@DiscriminatorValue ( "range" ) // TODO: would never be used, not sure Hibernate is fine without any specification
public abstract class RangeItem<T> extends DataItem
{
	private T low, hi;
	
	protected RangeItem () {
		super ();
	}

	public RangeItem ( T low, T hi )
	{
		this.low = low;
		this.hi = hi;
	}

	public RangeItem ( T low, T hi, String sourceText )
	{
		super ( sourceText );
		this.low = low;
		this.hi = hi;
	}
	
	
	/**
	 * @see ValueItem#getValue(). 
	 */
	@Transient
	public T getLow ()
	{
		return low;
	}

	protected void setLow ( T low )
	{
		this.low = low;
	}

	@Transient
	public T getHi ()
	{
		return hi;
	}

	protected void setHi ( T hi )
	{
		this.hi = hi;
	}
	
	
  @Override
  public boolean equals ( Object o ) 
  {
  	if ( o == null ) return false;
  	if ( this == o ) return true;
  	if ( this.getClass () != o.getClass () ) return false;
  	
  	RangeItem<?> that = (RangeItem<?>) o;
    
    if ( this.getLow () != null ? !this.low.equals ( that.getLow () ) : that.getLow () != null ) return false;
    return this.getHi () != null ? this.hi.equals ( that.getHi () ) : that.getHi () == null;
  }
  
  @Override
  public int hashCode() 
  {
  	int result = this.getLow () == null ? 0 : this.low.hashCode ();
  	return 31 * result + ( this.getHi () == null ? 0 : this.hi.hashCode () ); 
  }

	@Override
	public String toString ()
	{
		return String.format ( 
			"%s { sourceText: %d, low: %s, hi: %s }", 
			this.getClass ().getSimpleName (), this.getSourceText (), this.getLow (), this.getHi ()
		);
	}  	
  
}
