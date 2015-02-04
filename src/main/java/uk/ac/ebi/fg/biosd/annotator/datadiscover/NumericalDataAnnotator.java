package uk.ac.ebi.fg.biosd.annotator.datadiscover;

import java.text.ParseException;
import java.util.Date;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.DateUtils;

import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotationManager;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.ExtendedDiscoveredTerm;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.OntoTermResolverAndAnnotator;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.DBStore;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.normalizers.toplevel.AnnotationNormalizer;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.expgraph.properties.Unit;
import uk.ac.ebi.fg.core_model.expgraph.properties.dataitems.DataItem;
import uk.ac.ebi.fg.core_model.expgraph.properties.dataitems.DateItem;
import uk.ac.ebi.fg.core_model.expgraph.properties.dataitems.NumberItem;
import uk.ac.ebi.fg.core_model.expgraph.properties.dataitems.NumberRangeItem;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.expgraph.properties.dataitems.DataItemDAO;
import uk.ac.ebi.fg.core_model.terms.AnnotationType;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.toplevel.Annotation;
import uk.ac.ebi.fg.core_model.toplevel.AnnotationProvenance;
import uk.ac.ebi.fg.core_model.toplevel.TextAnnotation;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer.DiscoveredTerm;

/**
 * TODO: Comment me!
 *
 * <dl><dt>date</dt><dd>19 Nov 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class NumericalDataAnnotator
{
	private final OntologyTermDiscoverer ontoTermDiscoverer;
	private final OntoTermResolverAndAnnotator ontoTermResolver;
	
	public NumericalDataAnnotator ( OntologyTermDiscoverer ontoTermDiscoverer )
	{
		this.ontoTermResolver = new OntoTermResolverAndAnnotator ();
		this.ontoTermDiscoverer = ontoTermDiscoverer;
	}


	/**
	 * 
	 * @param pv
	 * @return true if it has actually found a number or date in the pval value. 
	 */
	public boolean annotate ( ExperimentalPropertyValue<ExperimentalPropertyType> pval, EntityManager em )
	{
		boolean result = annotateData ( pval, em );
		annotateUnit ( pval, em );
		return result;
	}
	
	
	private void annotateUnit ( ExperimentalPropertyValue<?> pval, EntityManager em )
	{
		Unit u = pval.getUnit ();
		if ( u == null ) return;
		
		OntologyEntry uoe = u.getOntologyTerms ().size () == 1 ? u.getSingleOntologyTerm () : null;
					
		if ( uoe == null || !ontoTermResolver.resolveOntoTerm ( uoe, em ) )
		{
			// No explicit and valid OE associated to the Unit, so use ZOOMA
			String unitLabel =  StringUtils.trimToNull ( u.getTermText () );
		
			// This are the ontology terms associated to the property value by ZOOMA
			// Only UO terms will be returned here
			for ( DiscoveredTerm dterm: ontoTermDiscoverer.getOntologyTermUris ( unitLabel, null ) )
			{
					EntityTransaction tx = em.getTransaction ();
					tx.begin ();
					OntologyEntry oe = ((ExtendedDiscoveredTerm) dterm).getOntologyTerm ();
					// This will save the term, if it's new.
					oe = em.merge ( oe );
					u.addOntologyTerm ( oe );
					tx.commit ();
			}
		}
	} // annotateUnit ()
		
	
	/**
	 * TODO: comment me! 
	 * 
	 * @param pval
	 * @return true if it has actually found a number or date in the pval value. 
	 */
	private boolean annotateData ( ExperimentalPropertyValue<?> pval, EntityManager em )
	{
		String pvalStr = StringUtils.trimToNull ( pval.getTermText () );
		if ( pvalStr == null ) return false;
				
		DataItem dataItem = null;
		
		// Start checking a middle separator, to see if it is a range
		String chunks[] = pvalStr.substring ( 0, Math.min ( pvalStr.length (), 300 ) ).split ( "(\\-|\\.\\.|\\,)" );
		
		if ( chunks != null && chunks.length == 2 )
		{
			chunks [ 0 ] = StringUtils.trimToNull ( chunks [ 0 ] );
			chunks [ 1 ] = StringUtils.trimToNull ( chunks [ 1 ] );
			
			// Valid chunks?
			if ( chunks [ 0 ] != null && chunks [ 1 ] != null )
			{
				// Number chunks?
				if ( NumberUtils.isNumber ( chunks [ 0 ] ) && NumberUtils.isNumber ( chunks [ 1 ] ) )
				{
					try {
						double lo = Double.parseDouble ( chunks [ 0 ] );
						double hi = Double.parseDouble ( chunks [ 1 ] );
						dataItem = new NumberRangeItem ( lo, hi );
					} 
					catch ( NumberFormatException nex ) {
						// Just ignore all in case of problems
					}
				}
			} // if valid chunks
		} // if there are chunks
		
		// Is it a single number?
		else if ( NumberUtils.isNumber ( pvalStr ) ) 
			try {
				dataItem = new NumberItem ( Double.parseDouble ( pvalStr ) );
			}
			catch ( NumberFormatException nex ) {
				// Just ignore all in case of problems
		}

		else if ( pval.getUnit () != null )
		{
			// Or maybe a single date?
			// TODO: factorise these constants
			try {
				dataItem = new DateItem ( DateUtils.parseDate ( pvalStr, 
					"dd'/'MM'/'yyyy", "dd'/'MM'/'yyyy HH:mm:ss", "dd'/'MM'/'yyyy HH:mm", 
					"dd'-'MM'-'yyyy", "dd'-'MM'-'yyyy HH:mm:ss", "dd'-'MM'-'yyyy HH:mm",
					"yyyyMMdd", "yyyyMMdd'-'HHmmss", "yyyyMMdd'-'HHmm"  
				));
			}
			catch ( ParseException dex ) {
				// Just ignore all in case of problems
			}
		}
		
		if ( dataItem == null ) return false; 

		EntityTransaction tx = em.getTransaction ();
		tx.begin ();

		DataItemDAO diDao = new DataItemDAO ( DataItem.class, em );
		DataItem dbDataItem = diDao.find ( dataItem );
		
		if ( dbDataItem != null )
			// If this value is already in the DB, just reuse it
			dataItem = dbDataItem;
		else
		{
			// else, annotate it and save it all
			TextAnnotation marker = createDataAnnotatorMarker ();
			AnnotationNormalizer<Annotation> annNormalizer = new AnnotationNormalizer<Annotation> ( new DBStore ( em ) );
		
			annNormalizer.normalize ( marker );
			em.persist ( marker );
			
			dataItem.addAnnotation ( marker );
		}
		
		// Attach the (old or new) data item to the current pv
		pval.addDataItem ( dataItem );
		tx.commit ();

		return true;
		
	} // annotateData ()


	public static TextAnnotation createDataAnnotatorMarker ()
	{
		TextAnnotation result = new TextAnnotation ( 
			new AnnotationType ( "Automatic Extracted Numerical Data" ),
			"" 
		);
		
		result.setProvenance ( new AnnotationProvenance ( PropertyValAnnotationManager.PROVENANCE_MARKER ) );
		result.setTimestamp ( new Date () );
		
		return result;
	}

}
