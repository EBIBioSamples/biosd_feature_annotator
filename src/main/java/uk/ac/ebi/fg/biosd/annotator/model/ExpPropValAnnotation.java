package uk.ac.ebi.fg.biosd.annotator.model;

import javax.persistence.Entity;
import javax.persistence.Table;

import org.apache.commons.lang3.StringUtils;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>5 May 2015</dd>
 *
 */
@Entity
@Table ( name = "exp_prop_val_feature_ann" )
public class ExpPropValAnnotation extends FeatureAnnotation
{
	public ExpPropValAnnotation ( ExperimentalPropertyValue<?> pv )
	{
		this ( getPvalText ( pv ) );
	}
	
	public ExpPropValAnnotation ( String pvtext )
	{
		super ( pvtext );
	}


	public static String getPvalText ( ExperimentalPropertyValue<?> pv )
	{
		if ( pv == null ) return null;
		String pvalStr = StringUtils.trimToNull ( pv.getTermText () );
		if ( pvalStr == null ) return null; 
		if ( pvalStr.length () > AnnotatorResources.MAX_STRING_LEN ) return null;
		ExperimentalPropertyType ptype = pv.getType ();
		String ptypeStr = ptype == null ? "" : StringUtils.trimToEmpty ( ptype.getTermText () );
		if ( ptypeStr.length () > AnnotatorResources.MAX_STRING_LEN ) return null;
		if ( ptypeStr.length () > 0 ) ptypeStr += '|';
		return ptypeStr + pvalStr;
	}
}
