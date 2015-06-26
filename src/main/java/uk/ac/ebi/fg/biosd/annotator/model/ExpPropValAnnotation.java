package uk.ac.ebi.fg.biosd.annotator.model;

import java.io.Serializable;
import java.util.regex.Pattern;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Index;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.utils.regex.RegEx;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>5 May 2015</dd>
 *
 */
@Entity
@Table ( 
	name = "exp_prop_val_feature_ann", 
	indexes = @Index ( name = "pvann_term_uri", columnList = "term_uri" ) 
)
@IdClass ( ExpPropValAnnotation.Key.class )
@NamedQuery(
	name = "findByPv",
	query = "FROM ExpPropValAnnotation ann WHERE sourceText = :pvkey"
)
public class ExpPropValAnnotation extends AbstractOntoTermAnnotation
{
	private final static RegEx COMMENT_RE = new RegEx ( "(Comment|Characteristic)\\s*\\[\\s*(.+)\\s*\\]", Pattern.CASE_INSENSITIVE );

	public static class Key implements Serializable
	{
		private static final long serialVersionUID = 7364817671320065409L;
		
		protected String sourceText, ontoTermUri;

		public Key () {
			super ();
		}

		public Key ( String sourceText, String ontoTermUri ) {
			super ();
			this.sourceText = sourceText;
			this.ontoTermUri = ontoTermUri;
		}
		
		@Override
		public boolean equals ( Object obj )
		{
			if ( obj == null ) return false;
			if ( this == obj ) return true;
			if ( !( obj instanceof Key ) ) return false;
			
			Key other = (Key) obj;
			if ( sourceText == null ? other.sourceText != null : !sourceText.equals ( other.sourceText ) ) return false;
			if ( ontoTermUri == null ? other.ontoTermUri != null : !ontoTermUri.equals ( other.ontoTermUri ) ) return false;

			return true;
		}
		
		@Override
		public int hashCode () 
		{
			int result = this.sourceText == null ? 0 : this.sourceText.hashCode ();
			result = 31 * result + this.ontoTermUri == null ? 0 : this.ontoTermUri.hashCode ();
			return result;
		}
		
	} 
	
	public ExpPropValAnnotation ( ExperimentalPropertyValue<?> pv )
	{
		this ( getPvalText ( pv ) );
	}
	
	public ExpPropValAnnotation ( String pvtext )
	{
		super ( pvtext );
	}	
	

	@Id
	@Override
	public String getSourceText () {
		return super.getSourceText ();
	}

	@Id
	@Override
	public String getOntoTermUri () {
		return super.getOntoTermUri ();
	}

	
	public static String getPvalText ( ExperimentalPropertyValue<?> pv )
	{
		Pair<String, String> pair = getTypeAndVal ( pv ); 
		if ( pair == null ) return null; 

		return getPvalText ( pair.getLeft (), pair.getRight () );
	}
	
	public static Pair<String, String> getTypeAndVal ( ExperimentalPropertyValue<?> pv )
	{
		if ( pv == null ) return null;
		String pvalStr = pv.getTermText ();
		ExperimentalPropertyType ptype = pv.getType ();
		String ptypeStr = ptype == null ? "" : ptype.getTermText ();
		ptypeStr = getExpPropTypeLabel ( ptype );

		return getTypeAndval ( ptypeStr, pvalStr );
	}

	
	public static Pair<String, String> getTypeAndval ( String ptypeStr, String pvalStr )
	{
		pvalStr = StringUtils.trimToNull ( pvalStr );
		if ( pvalStr == null ) return null; 
		if ( pvalStr.length () > AnnotatorResources.MAX_STRING_LEN ) return null;
		
		ptypeStr = StringUtils.trimToEmpty ( ptypeStr );
		if ( ptypeStr.length () > AnnotatorResources.MAX_STRING_LEN ) return null;

		return Pair.of ( ptypeStr, pvalStr );
	}
	
	
	public static String getPvalText ( String ptypeStr, String pvalStr )
	{
		Pair<String, String> pair = getTypeAndval ( ptypeStr, pvalStr ); 
		if ( pair == null ) return null; 
		
		String result = pair.getLeft ();
		return result.isEmpty () ? pair.getRight () : result + "|" + pair.getRight ();
	}
	
	/**
	 * Extract the type label, considering things like Comment [ X ] (return 'X' in such cases).
	 */
	public static String getExpPropTypeLabel ( ExperimentalPropertyType ptype ) 
	{
		if ( ptype == null ) return null;
		String typeLabel = StringUtils.trimToNull ( ptype.getTermText () );
		if ( typeLabel == null ) return null;
		
		String[] chunks = COMMENT_RE.groups ( typeLabel );
		if ( chunks == null || chunks.length < 3 ) return typeLabel;
		return chunks [ 2 ];
	}

}
