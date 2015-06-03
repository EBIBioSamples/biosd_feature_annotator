package uk.ac.ebi.fg.biosd.annotator.model;

import javax.persistence.Entity;
import javax.persistence.NamedQuery;
import javax.persistence.Transient;


/**
 * TODO: Comment me!
 *
 * <dl><dt>date</dt><dd>23 Jun 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
@Entity
//@DiscriminatorValue ( "value" ) // TODO: would never be used, not sure Hibernate is fine without any specification
public abstract class ValueItem<T> extends DataItem
{
	private T value;
	
	protected ValueItem () {
		super ();
	}

	public ValueItem ( T value ) {
		this.value = value;
	}

	public ValueItem ( T value, String sourceText ) {
		super ( sourceText );
		this.value = value;
	}

	
	/**
	 * TODO: this prevents us from issuing polymorphic queries at top level. If they're needed, we should experiment
	 * @@Any (http://docs.jboss.org/hibernate/core/3.6/reference/en-US/html/mapping.html#mapping-types-anymapping)
	 */
	@Transient
	public T getValue ()
	{
		return value;
	}

	protected void setValue ( T value )
	{
		this.value = value;
	}

  @Override
  public boolean equals ( Object o ) 
  {
  	if ( o == null ) return false;
  	if ( this == o ) return true;
  	if ( this.getClass () != o.getClass () ) return false;
  	
    // Compare accessions if both are non-null, use identity otherwise
  	ValueItem<?> that = (ValueItem<?>) o;
    return ( this.getValue () == null | that.getValue () == null ) ? false : this.value.equals ( that.value );
  }
  
  @Override
  public int hashCode() {
  	return this.getValue () == null ? super.hashCode () : this.value.hashCode ();
  }

  @Override
  public String toString() {
    return this.getClass ().getSimpleName () + "{ sourceText: " + getSourceText () + ", value: '" + getValue () + "' }";
  }

}
