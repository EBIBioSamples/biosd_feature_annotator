package uk.ac.ebi.fg.biosd.annotator.persistence;

import java.util.Collection;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Table;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.model.AbstractOntoTermAnnotation;
import uk.ac.ebi.fg.biosd.annotator.model.ComputedOntoTerm;
import uk.ac.ebi.fg.biosd.annotator.model.DataItem;
import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.fg.biosd.annotator.model.FeatureAnnotation;
import uk.ac.ebi.fg.biosd.annotator.model.ResolvedOntoTermAnnotation;
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
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer.DiscoveredTerm;
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
	private Table<Class, String, Object> store = AnnotatorResources.getInstance ().getNewStore ();
	
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
		log.info ( "Saving instance of {}" + type.getName () );
		
		EntityTransaction tx = entityManager.getTransaction ();
		tx.begin ();
		int ct = 0;
		
		for ( Object o: store.row ( type ).values () )
		{
			Object eid = this.entityManager.getEntityManagerFactory ().getPersistenceUnitUtil ().getIdentifier ( o );
			Object edb = this.entityManager.find ( o.getClass (), eid );
			if ( edb != null ) continue;
			
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
}
