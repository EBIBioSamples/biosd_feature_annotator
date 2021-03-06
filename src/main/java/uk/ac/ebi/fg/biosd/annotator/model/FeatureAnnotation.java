package uk.ac.ebi.fg.biosd.annotator.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.MappedSuperclass;
import javax.persistence.Table;

import org.apache.commons.lang3.StringUtils;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.core_model.resources.Const;

/**
 * A model class for feature annotations. This top-level class essentially stores provenance information, together
 * with a timestamp and a possible annotation confidence score.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>5 May 2015</dd>
 *
 */
@MappedSuperclass
@Table ( indexes = {
	@Index ( columnList = "type" ),
	@Index ( columnList = "provenance" ),
	@Index ( columnList = "timestamp" ),
	@Index ( columnList = "score" ),
	@Index ( columnList = "notes" ),
	@Index ( columnList = "internal_notes" ),
})
public abstract class FeatureAnnotation
{
	private String sourceText;
	
	private String type;
	private String provenance;
	private Date timestamp;
	private Double score;
	private String notes;
	private String internalNotes;

	
	protected FeatureAnnotation () {
	}

	protected FeatureAnnotation ( String sourceText ) {
		this.sourceText = sourceText;
	}

	@Id
	@Column( name = "source_text", length = AnnotatorResources.MAX_STRING_LEN * 2 + 1 )
	public String getSourceText ()
	{
		return sourceText;
	}

	public void setSourceText ( String sourceText )
	{
		this.sourceText = sourceText;
	}

	@Column ( length = Const.COL_LENGTH_M )
  public String getType() {
    return type;
  }

  public void setType ( String type ) {
     this.type = type;
  }
  
  /**
   * A person or a software component that generated this annotation. 
   */
	@Column ( length = Const.COL_LENGTH_M )
  public String getProvenance ()
	{
		return provenance;
	}

	public void setProvenance ( String provenance )
	{
		this.provenance = provenance;
	}

	/**
	 * When this annotation was created or last updated. TODO: Do we need to distinguish between creation/update.
	 */
	public Date getTimestamp ()
	{
		return timestamp;
	}

	public void setTimestamp ( Date timestamp )
	{
		this.timestamp = timestamp;
	}

	
	/**
	 * A measurement of how good or significant this annotation. This can be something like a p-value, or a percentage,
	 * or anything like that. TODO: do we need something like 'scoreType'? For the moment, this can be associated to 
	 * the {@link #getProvenance() provenance} or can be stored into {@link #getInternalNotes() internalNotes}.
	 * 
	 */
	public Double getScore ()
	{
		return score;
	}

	public void setScore ( Double score )
	{
		this.score = score;
	}

	/**
	 * Notes that can possibly be shown to the end-user.
	 */
	@Column ( length = Const.COL_LENGTH_XL )
	public String getNotes ()
	{
		return notes;
	}

	public void setNotes ( String notes )
	{
		this.notes = notes;
	}

	/**
	 * Notes that are technical and are not supposed to be understood by the end user, e.g., computational conditions
	 * stored by the tool that computed this annotation.
	 */
	@Column ( name = "internal_notes", length = Const.COL_LENGTH_XL )
	public String getInternalNotes ()
	{
		return internalNotes;
	}

	public void setInternalNotes ( String internalNotes )
	{
		this.internalNotes = internalNotes;
	}

 
	@Override
  public String toString () 
  {
  	return String.format ( 
  		" %s { sourceString: '%s', type: '%s', timestamp: %tc, provenance: '%s', score: %f, notes: '%s', internalNotes: '%s' }", 
  		this.getClass ().getSimpleName (), this.getSourceText (), this.getType (),
  		this.getTimestamp (), this.getProvenance (), this.getScore (), StringUtils.abbreviate ( this.getNotes (), 20 ), 
  		StringUtils.abbreviate ( this.getInternalNotes (), 20 )
  	);
  }

}
