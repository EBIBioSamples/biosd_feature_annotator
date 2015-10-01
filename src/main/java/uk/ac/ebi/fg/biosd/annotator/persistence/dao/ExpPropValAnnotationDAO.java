package uk.ac.ebi.fg.biosd.annotator.persistence.dao;

import java.util.List;

import javax.persistence.EntityManager;

import org.hibernate.jpa.QueryHints;

import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AbstractDAO;

/**
 * A Dao for {@link ExpPropValAnnotation}.
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
	
	/**
	 * Finds all the ontology terms associated to a given text coming from {@link ExperimentalPropertyValue}, 
	 * where the sourceText is supposed to follow the criteria in 
	 * {@link ExpPropValAnnotation#getPvalText(ExperimentalPropertyValue)}.
	 */
	public List<ExpPropValAnnotation> findBySourceText ( String sourceText, boolean isReadOnly )
	{
		if ( sourceText == null ) return null; 
		
		return this.getEntityManager ()
			.createNamedQuery ( "pvAnn.findBySourceText", this.getManagedClass () )
			.setHint ( QueryHints.HINT_READONLY, true )
			.setParameter ( "sourceText", sourceText )
			.getResultList ();
	}
	
	/**
	 * isReadOnly = false.
	 */
	public List<ExpPropValAnnotation> findBySourceText ( String sourceText )
	{
		return findBySourceText ( sourceText, false );
	}

	/**
	 * Uses {@link ExpPropValAnnotation#getPvalText(ExperimentalPropertyValue)}.
	 */
	public List<ExpPropValAnnotation> findByExpPropVal ( ExperimentalPropertyValue<?> pv, boolean isReadOnly )
	{
		return findBySourceText ( ExpPropValAnnotation.getPvalText ( pv ), isReadOnly );
	}

	/**
	 * isReadOnly = false.
	 */
	public List<ExpPropValAnnotation> findByExpPropVal ( ExperimentalPropertyValue<?> pv )
	{
		return findByExpPropVal ( pv, false );
	}
	
}
