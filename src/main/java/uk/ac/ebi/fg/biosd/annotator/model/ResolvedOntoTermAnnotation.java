package uk.ac.ebi.fg.biosd.annotator.model;

import javax.persistence.AttributeOverride;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.apache.commons.lang3.StringUtils;

import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.xref.ReferenceSource;

import javax.persistence.Index;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>5 May 2015</dd>
 *
 */
@Entity
@Table ( 
	name = "resolved_oe_feature_ann", 
	indexes = {
		@Index ( name = "resoeann_pvkey", columnList = "source_text" ),
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
	
	public static String getOntoEntryText ( OntologyEntry oe )
	{
		if ( oe == null ) return null;
		String acc = StringUtils.trimToNull ( oe.getAcc () );
		if ( acc == null ) return null; 
		if ( acc.length () > 2000 ) return null;
		
		ReferenceSource src = oe.getSource ();
		String srcStr = src == null ? "" : StringUtils.trimToEmpty ( src.getAcc () );
		if ( srcStr.length () > 1999 ) return null;
		if ( srcStr.length () > 0 ) srcStr += '|';
		return srcStr + acc;
	}
}
