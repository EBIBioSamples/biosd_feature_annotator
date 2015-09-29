package uk.ac.ebi.fg.biosd.annotator.persistence.dao;

import java.util.List;

import javax.persistence.EntityManager;

import org.hibernate.jpa.QueryHints;

import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AbstractDAO;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>1 Sep 2015</dd>
 *
 */
public class ExpPropValAnnotationDAO extends AbstractDAO<ExpPropValAnnotation>
{

	public ExpPropValAnnotationDAO ( EntityManager entityManager )
	{
		super ( ExpPropValAnnotation.class, entityManager );
	}
	
	public List<ExpPropValAnnotation> findBySourceText ( String sourceText, boolean isReadOnly )
	{
		if ( sourceText == null ) return null; 
		
		return this.getEntityManager ()
			.createNamedQuery ( "pvAnn.findBySourceText", this.getManagedClass () )
			.setHint ( QueryHints.HINT_READONLY, true )
			.setParameter ( "sourceText", sourceText )
			.getResultList ();
	}
	
	public List<ExpPropValAnnotation> findBySourceText ( String sourceText )
	{
		return findBySourceText ( sourceText, false );
	}

	
	public List<ExpPropValAnnotation> findByExpPropVal ( ExperimentalPropertyValue<?> pv, boolean isReadOnly )
	{
		return findBySourceText ( ExpPropValAnnotation.getPvalText ( pv ), isReadOnly );
	}

	public List<ExpPropValAnnotation> findByExpPropVal ( ExperimentalPropertyValue<?> pv )
	{
		return findByExpPropVal ( pv, false );
	}
	
}
