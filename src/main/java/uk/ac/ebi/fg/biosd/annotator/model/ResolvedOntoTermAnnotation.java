package uk.ac.ebi.fg.biosd.annotator.model;

import static uk.ac.ebi.fg.biosd.annotator.resources.AnnotatorBioSDResources.TABLE_PREFIX;

import javax.persistence.AttributeOverride;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;

import org.apache.commons.lang3.StringUtils;

import uk.ac.ebi.fg.core_model.resources.Const;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.xref.ReferenceSource;

/**
 * An annotation that comes from the {@link OntoResolverAndAnnotator}.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>5 May 2015</dd>
 *
 */
@Entity
@Table ( 
	name = TABLE_PREFIX + "resolved_oe_ann", 
	indexes = {
		@Index ( name = "resoeann_term_uri", columnList = "term_uri" )
	}
)
@AttributeOverride ( name = "sourceText", column = @Column ( length = 4000, name = "source_text" ) )
public class ResolvedOntoTermAnnotation extends AbstractOntoTermAnnotation
{
	protected ResolvedOntoTermAnnotation () {
		super ();
	}
	
	public ResolvedOntoTermAnnotation ( OntologyEntry oe ) {
		super ( getOntoEntryText ( oe ) );
	}

	public ResolvedOntoTermAnnotation ( String oetext ) {
		super ( oetext );
	}
	
	/**
	 * Makes a textual representation of the ontology term that is suitable to be used as 
	 * {@link #getSourceText() source text} for a {@link ResolvedOntoTermAnnotation}. This is in practice the term 
	 * accession + source, with some processing, like space wrapping and checks for {@link Const#COL_LENGTH_URIS}. 
	 *  
	 */
	public static String getOntoEntryText ( OntologyEntry oe )
	{
		if ( oe == null ) return null;
		String acc = StringUtils.trimToNull ( oe.getAcc () );
		if ( acc == null ) return null;
		// TODO: move this to constant class
		if ( acc.length () > Const.COL_LENGTH_URIS ) return null;
		
		ReferenceSource src = oe.getSource ();
		String srcStr = src == null ? "" : StringUtils.trimToEmpty ( src.getAcc () );
		if ( srcStr.length () > Const.COL_LENGTH_URIS - 1 ) return null;
		if ( srcStr.length () > 0 ) srcStr += '|';
		return srcStr + acc;
	}
}
