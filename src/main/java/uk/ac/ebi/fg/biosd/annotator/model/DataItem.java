package uk.ac.ebi.fg.biosd.annotator.model;

import static uk.ac.ebi.fg.biosd.annotator.resources.FeatureAnnotatorResources.TABLE_PREFIX;

import javax.persistence.DiscriminatorColumn;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.Table;

import org.apache.commons.lang3.StringUtils;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;

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
	name = TABLE_PREFIX + "data_item", 
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
	
	public static String getPvalText ( String pvalText )
	{
		String result = StringUtils.trimToNull ( pvalText );
		if ( result == null ) return null;
		if ( result.length () > AnnotatorResources.MAX_STRING_LEN ) return null;
		
		return result;
	}
	
	public static String getPvalText ( ExperimentalPropertyValue<?> pv )
	{
		if ( pv == null ) return null;
		return getPvalText ( pv.getTermText () );
	}

	
}
