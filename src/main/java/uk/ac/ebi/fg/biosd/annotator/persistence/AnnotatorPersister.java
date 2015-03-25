package uk.ac.ebi.fg.biosd.annotator.persistence;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.DBStore;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.MemoryStore;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.Normalizer;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.normalizers.expgraph.properties.PropertyValueNormalizer;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.normalizers.expgraph.properties.UnitNormalizer;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.normalizers.terms.OntologyEntryNormalizer;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.normalizers.toplevel.AnnotatableNormalizer;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.expgraph.properties.Unit;
import uk.ac.ebi.fg.core_model.expgraph.properties.dataitems.DataItem;
import uk.ac.ebi.fg.core_model.expgraph.properties.dataitems.DateItem;
import uk.ac.ebi.fg.core_model.expgraph.properties.dataitems.DateRangeItem;
import uk.ac.ebi.fg.core_model.expgraph.properties.dataitems.NumberItem;
import uk.ac.ebi.fg.core_model.expgraph.properties.dataitems.NumberRangeItem;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.terms.OntologyEntryDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.toplevel.Annotatable;
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
	private AnnotatableNormalizer<Annotatable> annNormalizer = new AnnotatableNormalizer<Annotatable> ( dbStore );
	
	public long persist ()
	{
		long result = 
		+	persistType ( NumberItem.class, annNormalizer )
		+	persistType ( DateItem.class, annNormalizer )
		+	persistType ( NumberRangeItem.class, annNormalizer )
		+	persistType ( DateRangeItem.class, annNormalizer )
		+ persistType ( OntologyEntry.class, annNormalizer )
		+ persistType ( Unit.class, new UnitNormalizer ( dbStore ) )
		+ persistType ( (Class) ExperimentalPropertyValue.class, new PropertyValueNormalizer ( dbStore ) );
		
		this.entityManager.clear ();
		return result;
	}

	
	private <T extends Identifiable> long persistType ( Class<T> type, Normalizer<? super T> normalizer )
	{
		EntityTransaction tx = entityManager.getTransaction ();
		tx.begin ();
		int ct = 0;
		for ( Object o: store.row ( type ).values () )
		{
			@SuppressWarnings ( "unchecked" )
			T t = (T) o;
			if ( normalizer != null ) 
			{
				Long oldId = t.getId (); 
				Long oldUid = null;
				Unit u = null;
				if ( oldId != null )
				{
					// normalize() ignores entities with non-null IDs
					ReflectionUtils.invoke ( t, Identifiable.class, "setId", new Class<?>[] { Long.class }, (Long) null );
					
					if ( type.equals ( ExperimentalPropertyValue.class ) 
							 && ( u = ((ExperimentalPropertyValue<?>) t).getUnit () ) != null ) 
					{
						oldUid = u.getId ();
						ReflectionUtils.invoke ( u, Identifiable.class, "setId", new Class<?>[] { Long.class }, (Long) null );
					}
				}
				normalizer.normalize ( t );
				if ( oldId != null )
				{
					// restore
					ReflectionUtils.invoke ( t, Identifiable.class, "setId", new Class<?>[] { Long.class }, oldId );
					if ( u != null && oldUid != null )
						ReflectionUtils.invoke ( u, Identifiable.class, "setId", new Class<?>[] { Long.class }, oldUid );
				}
			}
			this.entityManager.merge ( t );

			if ( ++ct % 100000 == 0 ) {
				tx.commit ();
				tx.begin ();
			}
		}
		tx.commit ();
		return ct;
	}

}
