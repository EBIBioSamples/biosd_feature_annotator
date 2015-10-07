package uk.ac.ebi.fg.biosd.annotator.model;

import static uk.ac.ebi.fg.biosd.annotator.resources.AnnotatorBioSDResources.TABLE_PREFIX;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import uk.ac.ebi.fg.biosd.annotator.persistence.AnnotatorPersister;

/**
 * This is used to have a DB table where to store a saving lock @see {@link AnnotatorPersister} for details.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>3 Sep 2015</dd>
 *
 */
@Entity
@Table ( name = TABLE_PREFIX + "save_lock" )
public class Lock
{
	private int id = 0;

	@Id
	public int getId ()
	{
		return id;
	}

	public void setId ( int id )
	{
		this.id = id;
	}

}
