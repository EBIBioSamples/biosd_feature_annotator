package uk.ac.ebi.fg.biosd.annotator.model;

import javax.persistence.DiscriminatorColumn;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.Table;

/**
 * TODO: Comment me!
 *
 * <dl><dt>date</dt><dd>23 Jun 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
@Entity
@Inheritance( strategy = InheritanceType.SINGLE_TABLE )
@DiscriminatorColumn ( name = "data_item_type" )
@Table ( 
	name = "data_item", 
	indexes = { 
		@Index ( columnList = "number_val" ),
		@Index ( columnList = "date_val" ),
		@Index ( columnList = "number_low" ),
		@Index ( columnList = "number_hi" ),
		@Index ( columnList = "date_low" ),
		@Index ( columnList = "date_hi" )
	}
)
public abstract class DataItem extends FeatureAnnotation
{
	protected DataItem () {
		super ();
	}
	
	protected DataItem ( String sourceText ) {
		super ( sourceText );
	}
}
