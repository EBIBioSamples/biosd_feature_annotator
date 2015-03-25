package uk.ac.ebi.fg.biosd.annotator.persistence;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import uk.ac.ebi.fg.core_model.resources.Resources;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>26 Feb 2015</dd>
 *
 */
public class BatchTransactionManager
{
	private EntityManager entityManager = null;
	private int processedItems = 0;
	private int commitSize = 10000;
	
	private static Set<BatchTransactionManager> threadLocalinstances = new HashSet<BatchTransactionManager> ();
	
	private static ThreadLocal<BatchTransactionManager> threadLocalinstance = new ThreadLocal<BatchTransactionManager> () 
	{
		@Override
		protected BatchTransactionManager initialValue ()
		{
			BatchTransactionManager result = new BatchTransactionManager ();
			threadLocalinstances.add ( result );
			return result;
		}
	};
	
	public BatchTransactionManager () 
	{
	}
	
	public static BatchTransactionManager getThreadLocalInstance ()
	{
		return threadLocalinstance.get ();
	}

	
	public boolean begin ()
	{
		if ( this.entityManager != null ) return false;
			
		this.entityManager = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
		EntityTransaction ts = this.entityManager.getTransaction ();
		ts.begin ();
		return true;
	}

	public boolean commit ( boolean force )
	{
		if ( ! ( ++processedItems == commitSize || force ) ) return false; 
		
		EntityTransaction ts = this.entityManager.getTransaction ();
		ts.commit ();
		
		this.entityManager.close ();
		this.entityManager = null;
		this.processedItems = 0;
		
		return true;
	}

	public boolean commit ()
	{
		return this.commit ( false );
	}

	
	public int getCommitSize ()
	{
		return commitSize;
	}

	public void setCommitSize ( int commitSize )
	{
		this.commitSize = commitSize;
	}

	public EntityManager getEntityManager ()
	{
		return entityManager;
	}

	public int getProcessedItems ()
	{
		return processedItems;
	}

	
	public static boolean commitAll ()
	{
		boolean result = false;
		for ( BatchTransactionManager emp: threadLocalinstances )
		{
			if ( emp.entityManager == null ) break; // already closed
			result |= emp.commit ( true );
		}
		return result;
	}
	
	
	public void close ()
	{
		if ( this.entityManager != null ) 
		{
			EntityTransaction ts = this.entityManager.getTransaction ();
			ts.rollback ();
			this.entityManager.close ();
			this.entityManager = null;
			this.processedItems = 0;
		}
		threadLocalinstances.remove ( this );
	}
}
