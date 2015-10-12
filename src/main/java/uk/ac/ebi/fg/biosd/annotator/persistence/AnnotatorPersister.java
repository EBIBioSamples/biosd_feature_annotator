package uk.ac.ebi.fg.biosd.annotator.persistence;

import java.util.Collection;
import java.util.Date;
import java.util.List;

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
	public static final String LOCK_TIMEOUT_PROP = "uk.ac.ebi.fg.biosd.annotator.lock_timeout";
	
	private EntityManager entityManager = null;
	@SuppressWarnings ( "rawtypes" )
	private Table<Class, String, Object> store = AnnotatorResources.getInstance ().getStore ();
	
	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	/**
	 * All the job here in this entry point.
	 * 
	 */
	public long persist ()
	{
		log.info ( StringUtils.center ( " Persisting annotations gathered so far, please wait... ", 90, "-" ) );
		
		long ct = 0;
		
		ct = persistEntities ( DataItem.class );
		ct += persistEntities ( ComputedOntoTerm.class );
		ct += persistEntities ( ResolvedOntoTermAnnotation.class );
		ct += persistEntities ( ExpPropValAnnotation.class );
		
		return ct;
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
				EntityTransaction tx = null;
				try
				{
					// We noticed inter-process synch problems (during LSF running), so we prefer to get a new EM each time
					entityManager = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
					tx = entityManager.getTransaction ();

					lock ();
					
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
					if ( tx != null && tx.isActive () )
						// Well, it shouldn't be, probably there's an error and hence let's rollback
						tx.rollback ();
					
					unlock ();
					
					if ( entityManager != null && entityManager.isOpen () ) entityManager.close ();
				}		
			
			} // run ()
			
		}); // MultipleAttemptsExecutor

		return result [ 0 ];
		
	} // persistEntities

	/** 
	 * <p>Invoked by {@link #persistEntities(Class)}. Implements a custom DB-level lock, to be used to isolate the persistence
	 * of the {@link AnnotatorResources#getStore() annotator store}. This is necessary to coordinate multiple annotation
	 * processes, running in separated JVMs (we use the LSF cluster). Transactions are not enough for this, because 
	 * our transactions are too long and end up to cause timeout errors to waiting processes (mainly due to optimistic
	 * locking).</p>
	 * 
	 * <p>Regarding the details on how the locking is implemented, we use the {@link Lock} entity. Usually the LSF invoking
	 * scripts call {@link AnnotateCmd} with the --unlock option, which causes {@link #forceUnlock()} and hence {@link #unlock()}
	 * to empty the Lock table and refill it with a single record, having status = 0 (unlocked). When {@link #lock()} is 
	 * invoked, it checks if such record has status = 0, or its {@link Lock#getTimestamp() timestamp} is expired (see
	 * {@link #LOCK_TIMEOUT_PROP}, if yes, it overwrites the record with 1 + current time. From now on, any other invocation
	 * of {@link #lock()} will wait for such record to become 0 again, or to expire. Note that, when Lock is empty, we
	 * assume that we are running in single-node mode (no LSF), so this locking is obtained immediately, a 0-status
	 * lock record is created at the end, by unlock. Also note that it might occasionally happen that two processes
	 * get a lock at the same time, if the current active lock is expired. We prefer to risk this with a timeout, rather 
	 * than blocking everything because of some interrupted process. Set a reasonable timeout to minimise collisions 
	 * (default is 30min)</p>
	 * 
	 * <p>Note that {@link #lock()} and {@link #unlock()} assumes {@link #entityManager} is already created.</p>
	 * 
	 */
	private void lock ()
	{
		EntityTransaction tx = null;
		
		try
		{
			// Wait a random time, to minimise collisions and unnecessary attempts to write
			// on an already-locked table (seems to happen without exceptions, due to optimistic locking
			//
			Thread.sleep ( RandomUtils.nextLong ( 0, 2001 ) );
			long timeout = 1000 * Long.valueOf ( System.getProperty ( LOCK_TIMEOUT_PROP, "" + 30 * 60 ) );
			
			tx = this.entityManager.getTransaction ();

			Lock lockrec = null;
			
			while ( true )
			{
				tx.begin ();
				
				// One record is prepared by the LSF-launching script (-k option).
				@SuppressWarnings ( "unchecked" )
				List<Lock> lockrecs = this.entityManager.createNativeQuery ( 
					"SELECT * FROM fann_save_lock ORDER BY lock_ts DESC FOR UPDATE", Lock.class 
				).getResultList ();
				
				// No locking, might happen when LSF has never been run, we (hopefully) are running a single process 
				// so locking isn't even needed.
				if ( lockrecs.size () == 0 ) break;
				
				lockrec = lockrecs.get ( 0 ); 
				
				// The lock is free if the existing record has 0 status, or if it's expired
				// This means two processes might write this record twice and have a lock both. This is the more unlikely
				// the higher the timeout, but with high timeouts you risk to hang the whole system, so we've a default
				// of 30 mins.
				if ( lockrec.getStatus () == 0 || System.currentTimeMillis () > lockrec.getTimestamp ().getTime () + timeout )
					break;
				
				tx.rollback ();
				// Again, wait a random time, to avoid concurrent locking of expired locks
				Thread.sleep ( RandomUtils.nextLong ( 3000, 6001 ) );
			}
			
			// In LSF mode (-k option), an empty record is prepared in the table. If it's not here,
			// assume we're not working in multi-process mode, no lock is needed.
			//
			if ( lockrec != null ) 
			{
				lockrec.setStatus ( 1 );
				lockrec.setTimestamp ( new Date () );
				this.entityManager.merge ( lockrec );
			}
			tx.commit ();
		}
		catch ( InterruptedException ex )
		{
			throw new RuntimeException ( 
				"Internal error while trying to get a process lock for saving annotations: " + ex.getMessage (), 
			ex );
		}
		finally {
			if ( tx != null && tx.isActive () ) tx.rollback ();
		}
	}
	
	/**
	 * @see #lock().
	 */
	private void unlock () 
	{
		EntityTransaction tx = null;
		try
		{
			tx = this.entityManager.getTransaction ();
			tx.begin ();
			// Usually it's only one, but just in case
			this.entityManager.createNativeQuery ( "DELETE FROM fann_save_lock" ).executeUpdate ();
			Lock newLockRec = new Lock ();
			newLockRec.setStatus ( 0 );
			newLockRec.setTimestamp ( new Date () );
			this.entityManager.persist ( newLockRec );
			tx.commit ();
		}
		finally {
			if ( tx != null && tx.isActive () ) tx.rollback ();
		}
	}
	
	/**
	 * <p>Used in tests and in a {@link AnnotateCmd command line} option. Removes {@link #lock() DB locks} left over 
	 * (e.g. by system crashes). Obviously, you need to know what you're doing, when using this option.</p>
	 * 
	 * This creates an {@link #entityManager} for the {@link #unlock()} operation, and closes it at the end.
	 * 
	 */
	public void forceUnlock ()
	{
		try {
			if ( this.entityManager == null ) 
				this.entityManager = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();

			this.unlock ();
		}
		finally {
			if ( entityManager != null && entityManager.isOpen () ) entityManager.close ();
		}
	}

}
