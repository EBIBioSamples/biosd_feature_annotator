package uk.ac.ebi.fg.biosd.annotator.datadiscover;

import java.text.ParseException;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.DateUtils;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotationManager;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.ExtendedDiscoveredTerm;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.OntoResolverAndAnnotator;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.expgraph.properties.Unit;
import uk.ac.ebi.fg.core_model.expgraph.properties.dataitems.DataItem;
import uk.ac.ebi.fg.core_model.expgraph.properties.dataitems.DateItem;
import uk.ac.ebi.fg.core_model.expgraph.properties.dataitems.NumberItem;
import uk.ac.ebi.fg.core_model.expgraph.properties.dataitems.NumberRangeItem;
import uk.ac.ebi.fg.core_model.terms.AnnotationType;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
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
	private final OntoResolverAndAnnotator ontoTermResolver;
	
	public NumericalDataAnnotator ( OntologyTermDiscoverer ontoTermDiscoverer )
	{
		this.ontoTermResolver = new OntoResolverAndAnnotator ();
		this.ontoTermDiscoverer = ontoTermDiscoverer;
	}


	/**
	 * 
	 * @param pv
	 * @return true if it has actually found a number or date in the pval value. 
	 */
	public boolean annotate ( ExperimentalPropertyValue<ExperimentalPropertyType> pval )
	{
		boolean result = annotateData ( pval );
		annotateUnit ( pval );
		return result;
	}
	
	
	private void annotateUnit ( ExperimentalPropertyValue<?> pval )
	{
		Unit u = pval.getUnit ();
		if ( u == null ) return;
		
		OntologyEntry uoe = u.getOntologyTerms ().size () == 1 ? u.getSingleOntologyTerm () : null;
					
		if ( uoe == null || !ontoTermResolver.annotate ( u ) )
		{
			// No explicit and valid OE associated to the Unit, so use ZOOMA
			String unitLabel =  StringUtils.trimToNull ( u.getTermText () );
			
			// This are the ontology terms associated to the property value by ZOOMA
			// Only UO terms will be returned here
			for ( DiscoveredTerm dterm: ontoTermDiscoverer.getOntologyTermUris ( unitLabel, null ) )
			{
					OntologyEntry oe = ((ExtendedDiscoveredTerm) dterm).getOntologyTerm ();
					u.addOntologyTerm ( oe );
			}
		}
	} // annotateUnit ()
		
	
	/**
	 * TODO: comment me! 
	 * 
	 * @param pval
	 * @return true if it has actually found a number or date in the pval value. 
	 */
	private boolean annotateData ( ExperimentalPropertyValue<?> pval )
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

		AnnotatorResources annResources = AnnotatorResources.getInstance ();
		DataItem diS = annResources.getStore ().find ( dataItem );
		
		if ( diS == null )
		{
			// annotate the new DI
			TextAnnotation marker = createDataAnnotatorMarker ();
			dataItem.addAnnotation ( marker );
		}
		else
			// Reuse the existing one
			dataItem = diS;
		
		// Attach the (old or new) data item to the current pv
		pval.addDataItem ( dataItem );

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
		
		AnnotatorResources.getInstance ().getAnnNormalizer ().normalize ( result );

		return result;
	}

}
