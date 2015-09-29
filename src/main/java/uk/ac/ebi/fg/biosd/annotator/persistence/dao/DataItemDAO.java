package uk.ac.ebi.fg.biosd.annotator.persistence.dao;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.hibernate.annotations.QueryHints;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.model.DataItem;
import uk.ac.ebi.fg.biosd.annotator.model.DateItem;
import uk.ac.ebi.fg.biosd.annotator.model.DateRangeItem;
import uk.ac.ebi.fg.biosd.annotator.model.NumberItem;
import uk.ac.ebi.fg.biosd.annotator.model.NumberRangeItem;
import uk.ac.ebi.fg.biosd.annotator.model.RangeItem;
import uk.ac.ebi.fg.biosd.annotator.model.ValueItem;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AbstractDAO;

/**
 * TODO: Comment me!
 *
 * <dl><dt>date</dt><dd>26 Jun 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class DataItemDAO extends AbstractDAO<DataItem>
{
	public DataItemDAO ( EntityManager entityManager )
	{
		super ( DataItem.class, entityManager );
	}
	
	
	@SuppressWarnings ( "unchecked" )
	public <D extends DataItem> D findByText ( String valueText, boolean isReadOnly ) 
	{
		valueText = StringUtils.trimToNull ( valueText );
		if ( valueText == null || valueText.length () > AnnotatorResources.MAX_STRING_LEN ) return null;
		
		Session session = (Session) this.getEntityManager ().getDelegate ();
		session.setDefaultReadOnly ( isReadOnly );
		
		return (D) session.get ( DataItem.class, valueText );
	}
	
	public <D extends DataItem> D findByText ( String valueText )
	{
		return findByText ( valueText, false );
	}

	
	public <D extends DataItem> D findByText ( ExperimentalPropertyValue<?> pv, boolean isReadOnly )
	{
		if ( pv == null ) return null;
		return findByText ( pv.getTermText (), isReadOnly );
	}
	
	public <D extends DataItem> D findByText ( ExperimentalPropertyValue<?> pv )
	{
		return findByText ( pv, false );
	}

	
	
	@SuppressWarnings ( "unchecked" )
	public <D extends DataItem> List<D> find ( D di, boolean getFirstOnly, boolean isReadOnly )
	{
		String qref = null;
		ValueItem<?> val = null;
		RangeItem<?> range = null;
		
		if ( di instanceof NumberItem ) {
			qref = "numberItem.find"; val = (ValueItem<?>) di; 
		}
		else if ( di instanceof DateItem ) {
			qref = "dateItem.find"; val = (ValueItem<?>) di; 
		}
		else if ( di instanceof NumberRangeItem ) {
			qref = "numberRangeItem.find"; range = (RangeItem<?>) di;
		}
		else if ( di instanceof DateRangeItem ) {
			qref = "dateRangeItem.find"; range = (RangeItem<?>) di;
		}
		else throw new RuntimeException ( 
			"Internal error: DataItem DAO cannot deal with the type " + di.getClass ().getName () 
		);
				
		Query q = this.getEntityManager ().createNamedQuery ( qref );
		q.setHint ( QueryHints.READ_ONLY, isReadOnly );

		if ( val != null ) 
			q.setParameter ( "value", val.getValue () );
		else
			q.setParameter ( "low", range.getLow () )
			 .setParameter ( "hi", range.getHi () );		
		
		
		if ( getFirstOnly ) q.setMaxResults ( 1 );

		return q.getResultList ();
	}
	
	public <D extends DataItem> D find ( D di )
	{
		List<D> results = find ( di, true, false );
		return results.isEmpty () ? null : results.iterator ().next ();
	}
	
	
	
	public int purge ()
	{
		EntityManager em = this.getEntityManager ();
		int result = em.createQuery ( 
			"DELETE FROM DataItem di WHERE di.id NOT IN (\n" +
			"  SELECT jdi.id FROM ExperimentalPropertyValue pv JOIN pv.dataItems jdi )" ).executeUpdate ();
		return result;
	}
}
