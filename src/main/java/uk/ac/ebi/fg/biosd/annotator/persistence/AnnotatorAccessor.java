package uk.ac.ebi.fg.biosd.annotator.persistence;

import static uk.ac.ebi.fg.biosd.annotator.model.AbstractOntoTermAnnotation.NULL_TERM_URI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.persistence.EntityManager;

import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.Session;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.model.ComputedOntoTerm;
import uk.ac.ebi.fg.biosd.annotator.model.DataItem;
import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.fg.biosd.annotator.model.ResolvedOntoTermAnnotation;
import uk.ac.ebi.fg.biosd.annotator.persistence.dao.DataItemDAO;
import uk.ac.ebi.fg.biosd.annotator.persistence.dao.ExpPropValAnnotationDAO;
import uk.ac.ebi.fg.biosd.annotator.persistence.dao.ResolvedOntoTermAnnotationDAO;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.expgraph.properties.Unit;
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
	private ResolvedOntoTermAnnotationDAO resolvedOntoTermAnnotationDAO;
	private DataItemDAO dataItemDAO;
	
	private static final DataItem NULL_DATA_ITEM = new DataItem() {};
	
	public AnnotatorAccessor ( EntityManager entityManager )
	{
		this.setEntityManager ( entityManager );
	}


	public List<ExpPropValAnnotation> getExpPropValAnnotations ( ExperimentalPropertyValue<?> pv )
	{
		String pvkey = ExpPropValAnnotation.getPvalText ( pv );
		if ( pvkey == null ) return null; 
		
		@SuppressWarnings ( "rawtypes" )
		Table<Class, String, Object> store = AnnotatorResources.getInstance ().getStore ();
		
		@SuppressWarnings ( "unchecked" )
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
	
	public List<OntologyEntry> getExpPropValAnnotatationsAsOntologyEntries ( ExperimentalPropertyValue<?> pv )
	{
		return ExpPropValAnnotation.asOntologyEntries ( getExpPropValAnnotations ( pv ) );
	}
	
	
	public DataItem getExpPropValDataItem ( ExperimentalPropertyValue<?> pv )
	{
		String pvstr = DataItem.getPvalText ( pv );
		if ( pvstr == null ) return null; 
		
		@SuppressWarnings ( "rawtypes" )
		Table<Class, String, Object> store = AnnotatorResources.getInstance ().getStore ();

		DataItem dataItem = (DataItem) store.get ( DataItem.class, pvstr );
		
		if ( dataItem == null )
		{
			// Not in the cache, see if it's in the DB
			//
			dataItem = this.dataItemDAO.findByText ( pvstr, true );
			if ( dataItem == null ) dataItem = NULL_DATA_ITEM;
			store.put ( DataItem.class, pvstr, dataItem );
		}
		
		if ( dataItem == NULL_DATA_ITEM ) 
			return null;
		
		return dataItem;
	}
	
	
	public Pair<ComputedOntoTerm, ResolvedOntoTermAnnotation> getResolvedOntoTerm ( OntologyEntry oe )
	{
		String oekey = ResolvedOntoTermAnnotation.getOntoEntryText ( oe );
		if ( oekey == null ) return null; 
		
		@SuppressWarnings ( "rawtypes" )
		Table<Class, String, Object> store = AnnotatorResources.getInstance ().getStore ();
		
		ResolvedOntoTermAnnotation oeann = (ResolvedOntoTermAnnotation) store.get (
			ResolvedOntoTermAnnotation.class, oekey 
		);
		
		if ( oeann == null )
		{
			// Not in the cache, see if it's in the DB
			//
			oeann = this.resolvedOntoTermAnnotationDAO.findByOntoTerm ( oe, true );
			
			if ( oeann == null )
				// This is an annotation with null URI, which marks the fact we don't have anything available for this 
				// ontology entry
				oeann = new ResolvedOntoTermAnnotation ( oekey );
			
			store.put ( ExpPropValAnnotation.class, oekey, oeann );
		}
		
		String oeComputedUri = oeann.getOntoTermUri ();
		if ( oeComputedUri == null )
			return null;
		
		
		// Now get the Ontology term, either from the cache, or from the DB again
		//
		
		ComputedOntoTerm oterm = (ComputedOntoTerm) store.get ( ComputedOntoTerm.class, oeComputedUri );
		
		if ( oterm == null )
		{
			Session session = (Session) this.entityManager.getDelegate ();
			session.setDefaultReadOnly ( true );
			oterm = (ComputedOntoTerm) session.get ( ComputedOntoTerm.class, oeComputedUri );
			if ( oterm == null ) throw new RuntimeException ( 
				"Internal error: can't find any ComputedOntoTerm for the URI <" + oeComputedUri + ">"  
			);
			store.put ( ComputedOntoTerm.class, oeComputedUri, oterm );
		}
		
		return Pair.of ( oterm, oeann );
	}

	
	
	public OntologyEntry getResolvedOntoTermAsOntologyEntry ( OntologyEntry oe, boolean returnOriginal )
	{
		Pair<ComputedOntoTerm, ResolvedOntoTermAnnotation> resolvedAnn = this.getResolvedOntoTerm ( oe );
		if ( resolvedAnn == null ) return returnOriginal ? oe : null ;
		
		ComputedOntoTerm computedOT = resolvedAnn.getLeft ();
		if ( computedOT == null ) return returnOriginal ? oe : null;
		
		return computedOT.asOntologyEntry ();
	}
	
	
	public OntologyEntry getResolvedOntoTermAsOntologyEntry ( OntologyEntry oe )
	{
		return getResolvedOntoTermAsOntologyEntry ( oe, true );
	}

	
	public List<OntologyEntry> getAllOntologyEntries ( ExperimentalPropertyValue<?> pv )
	{
		List<OntologyEntry> result = this.getExpPropValAnnotatationsAsOntologyEntries ( pv );
		if ( result == null ) result = new ArrayList<OntologyEntry> ();

		for ( OntologyEntry oe: pv.getOntologyTerms () )
			result.add ( this.getResolvedOntoTermAsOntologyEntry ( oe ) );
		
		return result;
	}
	
	
	public OntologyEntry getUnitOntologyEntry ( Unit u )
	{
		if ( u == null ) return null;
		
		OntologyEntry uoe = u.getOntologyTerms ().size () == 1 ? u.getSingleOntologyTerm () : null;
		if ( uoe == null ) return null;

		return getResolvedOntoTermAsOntologyEntry ( uoe );
	}
	
	
	public void setEntityManager ( EntityManager entityManager )
	{
		this.entityManager = entityManager;
		if ( this.expPropValAnnotationDAO == null ) 
		{
			this.expPropValAnnotationDAO = new ExpPropValAnnotationDAO ( entityManager );
			this.resolvedOntoTermAnnotationDAO = new ResolvedOntoTermAnnotationDAO ( entityManager );
			this.dataItemDAO = new DataItemDAO ( entityManager );
		}
		else 
		{
			this.expPropValAnnotationDAO.setEntityManager ( entityManager );
			this.resolvedOntoTermAnnotationDAO.setEntityManager ( entityManager );
			this.dataItemDAO.setEntityManager ( entityManager );
		}
	}

	/**
	 * invokes {@link #entityManager}.close().
	 */
	public void close ()
	{
		if ( this.entityManager != null && this.entityManager.isOpen () ) this.entityManager.close ();
	}

	/**
	 * Invokes {@link #close()}. 
	 */
	@Override
	protected void finalize () throws Throwable
	{
		this.close ();
		super.finalize ();
	}
	
}
