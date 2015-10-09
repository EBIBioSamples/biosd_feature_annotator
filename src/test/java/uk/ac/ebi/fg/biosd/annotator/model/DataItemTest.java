package uk.ac.ebi.fg.biosd.annotator.model;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.joda.time.DateTime;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import uk.ac.ebi.fg.biosd.annotator.persistence.dao.DataItemDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.utils.test.junit.TestEntityMgrProvider;

/**
 * Tests for {@link DataItem}.
 *
 * <dl><dt>date</dt><dd>25 Jun 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class DataItemTest
{
	@Rule
	public TestEntityMgrProvider emProvider = new TestEntityMgrProvider ( Resources.getInstance ().getEntityManagerFactory () );
	
	
	@Test
	public void testBasics ()
	{
		NumberItem n1 = new NumberItem ( 2.5 ), n2 = new NumberItem ( 3.5 ), n3 = new NumberItem ( n1.getValue () );
		
		assertEquals ( "n1.equals( n3 ) fails!", n1, n3 );
		assertFalse ( "!n2.equals( n1 ) fails!", n2.equals ( n1 ) );
		
		Set<NumberItem> nset = new HashSet<NumberItem> ();
		nset.addAll ( Arrays.asList ( new NumberItem[] { n1, n2, n3 } ) );
		assertEquals ( "hashCode() seems to fail!", 2, nset.size () );

		DateRangeItem dr1 = new DateRangeItem ( 
			new DateTime ( 2013, 1, 1, 0, 0 ).toDate (), new DateTime ( 2014, 12, 31, 0, 0 ).toDate () 
		);
		DateRangeItem dr2 = new DateRangeItem ( dr1.getLow (), dr1.getHi () );

		assertEquals ( "dr2.equals( dr1 ) fails!", dr2, dr1 );

		Set<DateRangeItem> dset = new HashSet<DateRangeItem> ();
		dset.addAll ( Arrays.asList ( new DateRangeItem[] { dr1, dr2 } ) );
		assertEquals ( "hashCode() seems to fail!", 2, nset.size () );
	}
	
	@Test
	@SuppressWarnings ( "unchecked" )
	public void testPersistence ()
	{
		NumberItem n1 = new NumberItem ( 2.5, "2.5" ), n2 = new NumberItem ( 3.5, "3.5" );
		DateRangeItem dr1 = new DateRangeItem ( 
			new DateTime ( 2013, 1, 1, 0, 0 ).toDate (), new DateTime ( 2014, 12, 31, 0, 0 ).toDate () 
		);
		dr1.setSourceText ( "20130101-20141231" );
				
		EntityManager em = emProvider.getEntityManager ();
		DataItemDAO dao = new DataItemDAO ( em );
		
		DataItem[] dataItems = new DataItem [] { n1, n2, dr1 };
		
		EntityTransaction ts = em.getTransaction ();
		ts.begin ();
		for ( DataItem di: dataItems ) dao.create ( di );
		ts.commit ();
		
		dao.setEntityManager ( em = emProvider.newEntityManager () );
		List<DataItem> dbItems = em.createQuery ( "from DataItem" ).getResultList ();
		assertEquals ( "DataItems reloading fails!", dataItems.length, dbItems.size () );
		
		// Clean-up
		ts = em.getTransaction ();
		ts.begin ();
		for ( DataItem di: dbItems ) dao.delete ( di );
		ts.commit ();
	}
	
	/**
	 * TODO: See {@link DataItemDAO}.
	 */
	@Test 
	@Ignore ( "Need some fixes, see DataItemDAO" )
	@SuppressWarnings ( "unchecked" )
	public void testExtremesPersistence ()
	{
		NumberItem n1 = new NumberItem ( 9.1E136, "9.1E136" );
				
		EntityManager em = emProvider.getEntityManager ();
		DataItemDAO dao = new DataItemDAO ( em );
				
		EntityTransaction ts = em.getTransaction ();
		ts.begin ();
		dao.create ( n1 );
		ts.commit ();
		
		dao.setEntityManager ( em = emProvider.newEntityManager () );
		List<DataItem> dbItems = em.createQuery ( "from DataItem" ).getResultList ();
		assertEquals ( "DataItems reloading fails!", 1, dbItems.size () );
		
		// Clean-up
		ts = em.getTransaction ();
		ts.begin ();
		for ( DataItem di: dbItems ) dao.delete ( di );
		ts.commit ();
	}
}
