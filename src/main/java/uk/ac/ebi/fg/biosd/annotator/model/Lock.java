package uk.ac.ebi.fg.biosd.annotator.model;

import static uk.ac.ebi.fg.biosd.annotator.resources.AnnotatorBioSDResources.TABLE_PREFIX;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import uk.ac.ebi.fg.biosd.annotator.persistence.AnnotatorPersister;
import uk.ac.ebi.fg.core_model.toplevel.Identifiable;

/**
 * This is used to have a DB table where to store a saving lock @see {@link AnnotatorPersister} for details.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>3 Sep 2015</dd>
 *
 */
@Entity
@Table ( name = TABLE_PREFIX + "save_lock" )
public class Lock extends Identifiable
{
	private int status = 0;
	private Date timestamp = null;
	
	public int getStatus ()
	{
		return status;
	}

	public void setStatus ( int status )
	{
		this.status = status;
	}

	@Column ( name = "lock_ts" ) @NotNull
	public Date getTimestamp ()
	{
		return timestamp;
	}

	public void setTimestamp ( Date timestamp )
	{
		this.timestamp = timestamp;
	}
	
}
