package uk.ac.ebi.fg.biosd.annotator.persistence;

import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.DBStore;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.MemoryStore;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.normalizers.expgraph.properties.PropertyValueNormalizer;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.normalizers.toplevel.AnnotationNormalizer;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.expgraph.properties.Unit;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.terms.OntologyEntryDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.FreeTextTerm;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.toplevel.Annotatable;
import uk.ac.ebi.fg.core_model.toplevel.Annotation;
import uk.ac.ebi.fg.core_model.toplevel.Identifiable;
import uk.ac.ebi.utils.reflection.ReflectionUtils;

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
	private MemoryStore store = ((SynchronizedStore) AnnotatorResources.getInstance ().getStore ()).getBase ();
	private DBStore dbStore = new DBStore ( this.entityManager );
	private AnnotationNormalizer<Annotation> annNormalizer = new AnnotationNormalizer<> ( dbStore );
	private PropertyValueNormalizer pvDbNormalizer = new PropertyValueNormalizer ( dbStore );
	private OntologyEntryDAO<OntologyEntry> oeDao = 
		new OntologyEntryDAO<OntologyEntry> ( OntologyEntry.class, entityManager );
	
	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	@SuppressWarnings ( "rawtypes" )
	public long persist ()
	{
		try
		{
			log.info ( "persisting annotations collected so far, please wait..." );
			EntityTransaction tx = entityManager.getTransaction ();
			tx.begin ();
			int ct = 0;
			
			for ( Object o: store.row ( OntologyEntry.class ).values () )
			{
				OntologyEntry oe = ( OntologyEntry) o;
				if ( log.isTraceEnabled () ) log.trace ( "persisting onto-entry {}", oe );

				ct += persistOntologyEntry ( oe );
	
				if ( ++ct % 100000 == 0 )
				{
					tx.commit ();
					log.info ( "committed {} items", ct );
					tx.begin ();
				}
			}
			
			tx.commit ();
			tx.begin ();
			
			for ( Object o: store.row ( ExperimentalPropertyValue.class ).values () )
			{
				ct += persistPropVal ( (ExperimentalPropertyValue) o );
	
				if ( ++ct % 100000 == 0 )
				{
					tx.commit ();
					log.info ( "committed {} items", ct );
					tx.begin ();
				}
			}
			tx.commit ();
			log.info ( "done, {} total items committed", ct );
			
			return ct;
		}
		finally {
			if ( this.entityManager.isOpen () ) this.entityManager.close ();
		}
		
	}

	
	private long persistPropVal ( ExperimentalPropertyValue<?> pv )
	{
		if ( log.isTraceEnabled () ) log.trace ( "persisting property {}", pv );
		
		long result = persistFreeTextTerm ( pv ) + 1;
		
		Unit u = pv.getUnit (); 
		if ( u != null ) result += persistFreeTextTerm ( u ) + 1;
		
		Long oldPvId = pv.getId ();
		
		// Enables the procedures in the normalizer
		ReflectionUtils.invoke ( pv, Identifiable.class, "setId", new Class<?>[] { Long.class }, (Long) null );
		
		// This will normalize the annotations and the data items.
		pvDbNormalizer.normalize ( pv );

		// Let's restore the ID
		ReflectionUtils.invoke ( pv, Identifiable.class, "setId", new Class<?>[] { Long.class }, oldPvId );

		// And now back to JPA
		this.entityManager.merge ( pv );
		
		return result;
	}	
	
	
	private long persistOntologyEntry ( OntologyEntry oe )
	{
		long result = persistAnnotatable ( oe );
		
		if ( oe.getId () != null ) {
			// Term already exists, just update the annotations
			this.entityManager.merge ( oe );
			return result;
		}
		
		// Term is new, let's see if this term accession/source already exists
		OntologyEntry oedb = oeDao.find ( oe );
		if ( oedb == null )
		{
			// Not there already, let's create it
			oeDao.create ( oe );
			return result;
		}
		
		// It's already here, let's pass our changes to it
		//
		oedb.setLabel ( oe.getLabel () );

		Set<Annotation> dbanns = oedb.getAnnotations ();
		for ( Annotation ann: oe.getAnnotations () ) 
		{
			ann = this.entityManager.merge ( ann );
			dbanns.add ( ann );
		}
		return result;
	}
	
	
	
	private long persistFreeTextTerm ( FreeTextTerm term )
	{
		long result = persistAnnotatable ( term );
		for ( OntologyEntry oe: term.getOntologyTerms () )
			// OEs are already done in the first part of the loop, so annotations only here
			result += persistAnnotatable ( oe ) + 1;
		return result;
	}
	
	
	private long persistAnnotatable ( Annotatable annotatable )
	{
		long result = 0;
		for ( Annotation ann: annotatable.getAnnotations () )
		{
			annNormalizer.normalize ( ann );
			result++;
		}
		return result;
	}
}
