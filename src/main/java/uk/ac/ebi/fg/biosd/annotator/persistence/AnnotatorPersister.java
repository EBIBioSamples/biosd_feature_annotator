package uk.ac.ebi.fg.biosd.annotator.persistence;

import java.sql.Connection;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.PersistenceException;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.cli.AnnotateCmd;
import uk.ac.ebi.fg.biosd.annotator.model.ComputedOntoTerm;
import uk.ac.ebi.fg.biosd.annotator.model.DataItem;
import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.fg.biosd.annotator.model.Lock;
import uk.ac.ebi.fg.biosd.annotator.model.ResolvedOntoTermAnnotation;
import uk.ac.ebi.fg.biosd.annotator.threading.PropertyValAnnotationService;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.utils.runcontrol.MultipleAttemptsExecutor;

import com.google.common.collect.Table;

/**
 * The annotator persister. This is called by {@link PropertyValAnnotationService}, when it finishes, or when the 
 * system memory needs to be flushed. Persists annotations in memory onto the BioSD database.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>19 Mar 2015</dd>
 *
 */
public class AnnotatorPersister
{
	public static final String LOCK_TIMEOUT_PROP = "uk.ac.ebi.debug.lock_timeout";
	
	private EntityManager entityManager = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
	@SuppressWarnings ( "rawtypes" )
	private Table<Class, String, Object> store = AnnotatorResources.getInstance ().getStore ();
	
	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	/**
	 * All the job here in this entry point.
	 * 
	 */
	public long persist ()
	{
		try
		{
			log.info ( StringUtils.center ( " Persisting annotations gathered so far, please wait... ", 110, "-" ) );
			
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

	/**
	 * Expects to find objects stored as instances of "type" in the {@link AnnotatorResources#getStore() annotator store}.
	 * Sends all of them to the DB, via Hibernate. Manages it all inside multiple transactions.
	 * 
	 */
	private <T> long persistEntities ( final Class<T> type )
	{
		final Collection<Object> objects = store.row ( type ).values ();
		int sz = objects == null ? 0 :  objects.size ();
		log.info ( "Saving {} instance(s) of {}", sz, type.getName () );
		if ( sz == 0 ) return 0;
		
		final Integer[] result = new Integer [ 1 ];
		
		// Try multiple times, it's so important that we complete it.
		//
		new MultipleAttemptsExecutor ( 5, 1000 * 60, 1000 * 60 * 5, PersistenceException.class )
		.execute ( new Runnable() {
			@Override
			public void run ()
			{
				lock ();
				EntityTransaction tx = entityManager.getTransaction ();
				try
				{
					tx.begin ();
					int ct = 0;

					for ( Object o: objects )
					{
						Object eid = entityManager.getEntityManagerFactory ().getPersistenceUnitUtil ().getIdentifier ( o );
						Object edb = entityManager.find ( o.getClass (), eid );
						if ( edb != null ) {
							if ( log.isTraceEnabled () ) log.trace ( "Object already in the DB: {}", edb.toString () );
							continue;
						}
										
						if ( log.isTraceEnabled () ) log.trace ( "Saving: {}", o.toString () );
						entityManager.persist ( o );
						
						if ( ++ct % 100000 == 0 )
						{
							tx.commit ();
							log.info ( "committed {} items", ct );
							tx.begin ();
						}
					}

					tx.commit ();
					log.info ( "done, {} total instances of {} committed", ct, type.getName () );
					
					result [ 0 ] = ct;
				}
				finally
				{
					if ( tx.isActive () )
						// Well, it shouldn't be, probably there's an error and hence let's rollback
						tx.rollback ();
					
					unlock ();
				}		
			
			} // run ()
			
		}); // MultipleAttemptsExecutor

		return result [ 0 ];
		
	} // persistEntities

	/**
	 * Invoked by {@link #persistEntities(Class)}. Implements a custom DB-level lock, to be used to isolate the persistence
	 * of the {@link AnnotatorResources#getStore() annotator store}. This is necessary to coordinate multiple annotation
	 * processes, running in separated JVMs (we use the LSF cluster). Transactions are not enough for this, because 
	 * our transactions are too long and end up to cause timeout errors to waiting processes. Optimistic locking is 
	 * not good either, because the persistence of most objects we deal with require {@link Connection#TRANSACTION_SERIALIZABLE}, 
	 * (to prevent <a href = "https://docs.oracle.com/javase/tutorial/jdbc/basics/transactions.html">phantom reads</a>),
	 * which doesn't easily play with optimistic locking. 
	 * 
	 */
	private void lock ()
	{
		try
		{
			// Wait a random time, to minimise collisions and unnecessary attempts to write
			// on an already-locked table (seems to happen without exceptions, due to optimistic locking
			//
			Thread.sleep ( RandomUtils.nextLong ( 0, 2001 ) );
			
			Map<String, Object> dbprops = this.entityManager.getEntityManagerFactory ().getProperties ();
			
			String driverName = StringUtils.trimToEmpty ( 
				(String) dbprops.get ( "hibernate.connection.driver_class" ) );
			
			String sqlSerialize = 
				driverName.contains ( ".h2." ) ? "SET LOCK_MODE = 1"
				: driverName.contains ( "Oracle" ) ? "SET TRANSACTION ISOLATION LEVEL SERIALIZABLE"
				: null;
			
			if ( sqlSerialize == null ) throw new RuntimeException ( 
				"Internal error: I don't know how to set serializable isolation level for '" + driverName + "'"
			);
			
			EntityTransaction tx = this.entityManager.getTransaction ();
			
			while ( true )
			{
				tx.begin ();

				this.entityManager.createNativeQuery ( sqlSerialize ).executeUpdate ();
				
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
	
	/**
	 * @see #lock().
	 */
	private void unlock () 
	{
		EntityTransaction tx = this.entityManager.getTransaction ();
		tx.begin ();
		this.entityManager.createNativeQuery ( "DELETE FROM fann_save_lock" ).executeUpdate ();
		Lock newLockRec = new Lock ();
		newLockRec.setStatus ( 0 );
		newLockRec.setTimestamp ( new Date () );
		this.entityManager.persist ( newLockRec );
		tx.commit ();
	}
	
	/**
	 * Used in tests and in a {@link AnnotateCmd command line} option. Removes {@link #lock() DB locks} left over 
	 * (e.g. by system crashes). Obviously, you need to know what you're doing, when using this option.
	 * 
	 */
	public void forceUnlock ()
	{
		this.unlock ();
	}
}
