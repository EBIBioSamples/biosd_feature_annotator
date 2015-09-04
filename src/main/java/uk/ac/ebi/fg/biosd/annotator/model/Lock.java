package uk.ac.ebi.fg.biosd.annotator.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>3 Sep 2015</dd>
 *
 */
@Entity
@Table ( name = "feature_annotator_save_lock" )
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
