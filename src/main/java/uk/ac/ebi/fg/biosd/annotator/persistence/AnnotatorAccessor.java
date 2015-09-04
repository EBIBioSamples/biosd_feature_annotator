package uk.ac.ebi.fg.biosd.annotator.persistence;

import static uk.ac.ebi.fg.biosd.annotator.model.AbstractOntoTermAnnotation.NULL_TERM_URI;

import java.util.Collections;
import java.util.List;

import javax.persistence.EntityManager;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.model.DataItem;
import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.fg.biosd.annotator.model.ResolvedOntoTermAnnotation;
import uk.ac.ebi.fg.biosd.annotator.persistence.dao.ExpPropValAnnotationDAO;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;

import com.google.common.collect.Table;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>1 Sep 2015</dd>
 *
 */
public class AnnotatorAccessor
{
	private EntityManager entityManager;
	private ExpPropValAnnotationDAO expPropValAnnotationDAO;
	
	public List<ExpPropValAnnotation> getExpPropValAnnotations ( ExperimentalPropertyValue<?> pv )
	{
		String pvkey = ExpPropValAnnotation.getPvalText ( pv );
		if ( pvkey == null ) return null; 
		
		Table<Class, String, Object> store = AnnotatorResources.getInstance ().getStore ();
		List<ExpPropValAnnotation> pvanns = (List<ExpPropValAnnotation>) store.get ( ExpPropValAnnotation.class, pvkey );
		
		if ( pvanns == null )
		{
			// Not in the cache, see if it's in the DB
			//
			pvanns = this.expPropValAnnotationDAO.findByExpPropVal ( pv, true );
			
			if ( pvanns == null 
					|| pvanns.size () == 1 
					   && NULL_TERM_URI.equals ( pvanns.iterator ().next ().getOntoTermUri () ) ) 
				pvanns = Collections.emptyList ();

			store.put ( ExpPropValAnnotation.class, pvkey, pvanns );
		}
		
		return pvanns;
	}
	
	public List<DataItem> getExpPropValDataItems ( ExpPropValAnnotation pv )
	{
		return null;
	}
	
	public List<ResolvedOntoTermAnnotation> getResolvedOntoTerms ( ExpPropValAnnotation pv )
	{
		return null;		
	}
	
	public ResolvedOntoTermAnnotation getResolvedOntoTerms ( OntologyEntry oe )
	{
		return null;		
	}

	public void setEntityManager ( EntityManager entityManager )
	{
		this.entityManager = entityManager;
		if ( this.expPropValAnnotationDAO == null )
			this.expPropValAnnotationDAO = new ExpPropValAnnotationDAO ( entityManager );
		else
			this.expPropValAnnotationDAO.setEntityManager ( entityManager );
	}
		
}
