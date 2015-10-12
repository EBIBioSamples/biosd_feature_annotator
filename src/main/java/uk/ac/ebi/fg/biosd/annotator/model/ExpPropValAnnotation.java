package uk.ac.ebi.fg.biosd.annotator.model;

import static uk.ac.ebi.fg.biosd.annotator.resources.AnnotatorBioSDResources.TABLE_PREFIX;

import java.io.Serializable;
import java.util.regex.Pattern;

import javax.persistence.Column;
import javax.persistence.Embeddable;
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
import uk.ac.ebi.fg.core_model.resources.Const;
import uk.ac.ebi.utils.regex.RegEx;

/**
 * Represents an ontology term associated to the text coming from an {@link ExperimentalPropertyValue}.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>5 May 2015</dd>
 *
 */
@Entity
@NamedQuery(
	name = "pvAnn.findBySourceText",
	query = "FROM ExpPropValAnnotation ann WHERE sourceText = :sourceText"
)
@Table ( 
	name = TABLE_PREFIX + "exp_pv_feature_ann", 
	indexes = {
		@Index ( name = "pvann_pvkey", columnList = "source_text" ),
		@Index ( name = "pvann_uri", columnList = "term_uri" )
	}
)
@IdClass ( ExpPropValAnnotation.Key.class )
public class ExpPropValAnnotation extends AbstractOntoTermAnnotation
{
	private final static RegEx COMMENT_RE = new RegEx ( "(Comment|Characteristic)\\s*\\[\\s*(.+)\\s*\\]", Pattern.CASE_INSENSITIVE );

	/**
	 * We have a composite key for this made of the source text and the ontlogy term URI that was associated to it. 
	 * Note that when we search for the terms associated to some text, we have the latter as key, to which multiple 
	 * terms may be associated.
	 *
	 * @author brandizi
	 * <dl><dt>Date:</dt><dd>1 Oct 2015</dd>
	 *
	 */
	@Embeddable
	public static class Key implements Serializable
	{
		private static final long serialVersionUID = 7364817671320065409L;
		
		protected String sourceText, ontoTermUri;

		protected Key () {
			super ();
		}

		public Key ( String sourceText, String ontoTermUri ) {
			super ();
			this.sourceText = sourceText;
			this.ontoTermUri = ontoTermUri;
		}
		
		public String getSourceText () {
			return sourceText;
		}

		public void setSourceText ( String sourceText ) {
			this.sourceText = sourceText;
		}

		public String getOntoTermUri () {
			return ontoTermUri;
		}

		public void setOntoTermUri ( String ontoTermUri ) {
			this.ontoTermUri = ontoTermUri;
		}
		
		@Override
		public boolean equals ( Object obj )
		{
			if ( obj == null ) return false;
			if ( this == obj ) return true;
			if ( !( obj instanceof Key ) ) return false;
			
			Key other = (Key) obj;
			if ( getSourceText () == null ? other.getSourceText () != null : !sourceText.equals ( other.getSourceText () ) ) 
				return false;
			if ( getOntoTermUri () == null ? other.getOntoTermUri () != null : !ontoTermUri.equals ( other.getOntoTermUri () ) ) 
				return false;

			return true;
		}

		@Override
		public int hashCode () 
		{
			int result = this.getSourceText () == null ? 0 : this.sourceText.hashCode ();
			result = 31 * result + this.getOntoTermUri () == null ? 0 : this.ontoTermUri.hashCode ();
			return result;
		}
		
	} 
	
	protected ExpPropValAnnotation () {
		super ();
	}

	public ExpPropValAnnotation ( ExperimentalPropertyValue<?> pv )
	{
		this ( getPvalText ( pv ) );
	}
	
	public ExpPropValAnnotation ( String pvtext )
	{
		super ( pvtext );
	}	
	

	/**
	 * @see #getTypeAndval(String, String)
	 */
	@Id
	@Column( name = "source_text", length = AnnotatorResources.MAX_STRING_LEN * 2 + 1 )
	@Override
	public String getSourceText () {
		return super.getSourceText ();
	}

	@Id
	@Column ( length = Const.COL_LENGTH_URIS, name = "term_uri" )
	@Override
	public String getOntoTermUri () {
		return super.getOntoTermUri ();
	}

	/**
	 * Invokes {@link #getTypeAndVal(ExperimentalPropertyValue)} and then {@link #getPvalText(String, String)}, 
	 * to obtain strings like "Organism|Homo Sapiens". 
	 */
	public static String getPvalText ( ExperimentalPropertyValue<?> pv )
	{
		Pair<String, String> pair = getTypeAndVal ( pv ); 
		if ( pair == null ) return null; 

		return getPvalText ( pair.getLeft (), pair.getRight () );
	}
	
	/**
	 * Decomposes the parameter into a pair of strings and pass them to {@link #getTypeAndval(String, String)}.
	 * Uses {@link #getExpPropTypeLabel(ExperimentalPropertyType)} to unwrap elements like Comment[] or Characteristic[].	 *  
	 */
	public static Pair<String, String> getTypeAndVal ( ExperimentalPropertyValue<?> pv )
	{
		if ( pv == null ) return null;
		String pvalStr = pv.getTermText ();
		ExperimentalPropertyType ptype = pv.getType ();
		String ptypeStr = ptype == null ? "" : ptype.getTermText ();
		ptypeStr = getExpPropTypeLabel ( ptype );

		return getTypeAndval ( ptypeStr, pvalStr );
	}

	/**
	 * Returns a canonical pair of strings, where the left is about the type (e.g., "Organism", and the right about the 
	 * value (e.g., "Homo Sapiens"). Does some pre-processing, such as space trimming or checking for 
	 * {@link AnnotatorResources#MAX_STRING_LEN}. We don't standardise into lower or upper case, cause the difference
	 * might be relevant in certain cases (e.g. units like mA, gene symbols).
	 * 
	 */
	public static Pair<String, String> getTypeAndval ( String ptypeStr, String pvalStr )
	{
		pvalStr = StringUtils.trimToNull ( pvalStr );
		if ( pvalStr == null ) return null; 
		if ( pvalStr.length () > AnnotatorResources.MAX_STRING_LEN ) return null;
		
		ptypeStr = StringUtils.trimToEmpty ( ptypeStr );
		if ( ptypeStr.length () > AnnotatorResources.MAX_STRING_LEN ) return null;

		return Pair.of ( ptypeStr, pvalStr );
	}
	
	/**
	 *  Takes {@link #getTypeAndval(String, String)} and builds strings like "Organism|Homo Sapiens", which can be
	 *  used as {@link #getSourceText()}.
	 */
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
