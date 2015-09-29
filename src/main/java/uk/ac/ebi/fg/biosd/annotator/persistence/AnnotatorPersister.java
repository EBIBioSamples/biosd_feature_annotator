package uk.ac.ebi.fg.biosd.annotator.persistence;

import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.model.ComputedOntoTerm;
import uk.ac.ebi.fg.biosd.annotator.model.DataItem;
import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.fg.biosd.annotator.model.ResolvedOntoTermAnnotation;
import uk.ac.ebi.fg.core_model.resources.Resources;

import com.google.common.collect.Table;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>19 Mar 2015</dd>
 *
 */
public class AnnotatorPersister
{
	private EntityManager entityManager = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
	private Table<Class, String, Object> store = AnnotatorResources.getInstance ().getStore ();
	
	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	public long persist ()
	{
		try
		{
			log.info ( "persisting annotations collected so far, please wait..." );
			long ct = 0;

			ct = persistEntities ( DataItem.class );
			ct += persistEntities ( ComputedOntoTerm.class );
			ct += persistEntities ( ResolvedOntoTermAnnotation.class );
			ct += persistEntities ( ExpPropValAnnotation.class );
			
			return ct;
		}
		finally {
			if ( this.entityManager.isOpen () ) this.entityManager.close ();
		}
		
	}

	
	private <T> long persistEntities ( Class<T> type )
	{
		Collection<Object> objects = store.row ( type ).values ();
		int sz = objects == null ? 0 :  objects.size ();
		log.info ( "Saving {} instances of {}", sz, type.getName () );
		if ( sz == 0 ) return 0;
		
		lock ();
		EntityTransaction tx = this.entityManager.getTransaction ();
		try
		{
			tx.begin ();
			int ct = 0;

			for ( Object o: objects )
			{
				Object eid = this.entityManager.getEntityManagerFactory ().getPersistenceUnitUtil ().getIdentifier ( o );
				Object edb = this.entityManager.find ( o.getClass (), eid );
				if ( edb != null ) {
					if ( log.isTraceEnabled () ) log.trace ( "Object already in the DB: {}", edb.toString () );
					continue;
				}
								
				if ( log.isTraceEnabled () ) log.trace ( "Saving: {}", o.toString () );
				this.entityManager.persist ( o );
				
				if ( ++ct % 100000 == 0 )
				{
					tx.commit ();
					log.info ( "committed {} items", ct );
					tx.begin ();
				}
			}

			tx.commit ();
			log.info ( "done, {} total instances of {} committed", ct, type.getName () );
			
			return ct;
		}
		finally
		{
			if ( tx.isActive () )
				// Well, it shouldn't be, probably there's an error and hence let's rollback
				tx.rollback ();
			
			unlock ();
		}		
	}


	private void lock ()
	{
		try
		{
			EntityTransaction tx = this.entityManager.getTransaction ();
			
			while ( true )
			{
				tx.begin ();
				
				@SuppressWarnings ( "unchecked" )
				List<Object> lockrec = this.entityManager.createNativeQuery ( 
					"SELECT id FROM fann_save_lock FOR UPDATE" 
				).getResultList ();
				
				if ( lockrec.size () == 0 ) break;

				tx.rollback ();
				Thread.sleep ( 3000 );
			}
			
			this.entityManager.createNativeQuery ( "INSERT INTO fann_save_lock VALUES ( 1 )" ).executeUpdate ();
			tx.commit ();
		}
		catch ( InterruptedException ex )
		{
			throw new RuntimeException ( 
				"Internal error while trying to get a process lock for saving annotations: " + ex.getMessage (), 
			ex );
		}
	}
	
	private void unlock () 
	{
		EntityTransaction tx = this.entityManager.getTransaction ();
		tx.begin ();
		this.entityManager.createNativeQuery ( "DELETE FROM fann_save_lock" ).executeUpdate ();
		tx.commit ();
	}
	
	public int forceUnlock ()
	{
		this.unlock ();
		return 1;
	}
}
