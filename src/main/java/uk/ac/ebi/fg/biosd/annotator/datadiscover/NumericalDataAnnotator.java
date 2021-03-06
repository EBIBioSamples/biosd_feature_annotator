package uk.ac.ebi.fg.biosd.annotator.datadiscover;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.DateUtils;

import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotationManager;
import uk.ac.ebi.fg.biosd.annotator.model.DataItem;
import uk.ac.ebi.fg.biosd.annotator.model.DateItem;
import uk.ac.ebi.fg.biosd.annotator.model.NumberItem;
import uk.ac.ebi.fg.biosd.annotator.model.NumberRangeItem;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.expgraph.properties.Unit;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.onto_discovery.api.OntologyTermDiscoverer;

import com.google.common.collect.Table;

/**
 * Extracts numbers, ranges, dates from the text value in {@link ExperimentalPropertyValue}. It also tries to find 
 * ontology terms for {@link ExperimentalPropertyValue#getUnit() units}.
 * 
 * Results are saved in {@link AnnotatorResources the common store}. 
 *
 * <dl><dt>date</dt><dd>19 Nov 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class NumericalDataAnnotator
{
	public static final String ANNOTATION_TYPE_MARKER = "Computed Numerical Data";
	
	private final OntologyTermDiscoverer unitOntoDiscoverer;
	
	/**
	 * @param unitOntoDiscoverer will be used to annotate a property value unit.
	 */
	public NumericalDataAnnotator ( OntologyTermDiscoverer ontoTermDiscoverer )
	{
		this.unitOntoDiscoverer = ontoTermDiscoverer;
	}


	/**
	 * Does the annotation job. 
	 * @return true if it has actually found a number or date in the pval value. 
	 */
	public boolean annotate ( ExperimentalPropertyValue<ExperimentalPropertyType> pval )
	{
		boolean result = annotateData ( pval );
		annotateUnit ( pval );
		return result;
	}
	
	/**
	 *  Find ontology terms about the unit in a property value. This uses the {@link OntoResolverAndAnnotator}, if 
	 *  one {@link Unit#getOntologyTerms() unit ontology term} is present in the BioSD DB, otherwise, it 
	 *  tries to {@link OntologyTermDiscoverer discover} an ontology entry from ontologies (usually the Unit Ontology).
	 */
	private void annotateUnit ( ExperimentalPropertyValue<?> pval )
	{
		Unit u = pval.getUnit ();
		if ( u == null ) return;
		
		OntologyEntry uoe = u.getOntologyTerms ().size () == 1 ? u.getSingleOntologyTerm () : null;
					
		if ( uoe == null )
		{
			// No explicit and valid OE associated to the Unit, so use ZOOMA
			String unitLabel =  StringUtils.trimToNull ( u.getTermText () );
						
			// This are the ontology terms associated to the property value by ZOOMA
			// Only UO terms will be returned here
			// The invocation saves the terms into memory, for later persistence, and that's all we need here.
			unitOntoDiscoverer.getOntologyTerms ( unitLabel, "Unit" );
		}
	} // annotateUnit ()
		
	
	/**
	 * Tries to extract numerical/date information from the text value of an experimental property value. Possibly
	 * creates and save an instance in the hierarchy of {@link DataItem}.
	 * 
	 * @return true if it has actually found a number or date in the pval value. 
	 */
	private boolean annotateData ( ExperimentalPropertyValue<?> pval )
	{
		String pvalStr = DataItem.getPvalText ( pval );
		if ( pvalStr == null ) return false;
				
		// Do we already have it?
		Table<Class, String, Object> store = AnnotatorResources.getInstance ().getStore ();
		
		synchronized ( pvalStr.intern () )
		{
			DataItem dataItem = (DataItem) store.get ( DataItem.class, pvalStr );
			if ( dataItem != null ) return true;
	
			// Start checking a middle separator, to see if it is a range
			String chunks[] = pvalStr.split ( "(\\-|\\.\\.|\\,)" );
			
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
							
							// TODO: We need fixes to be able to accommodate bigger numbers in Oracle
							if ( Math.abs ( lo ) < 10E125d && Math.abs ( hi ) < 10E125 )
								dataItem = lo <= hi ? new NumberRangeItem ( lo, hi ) : new NumberRangeItem ( hi, lo );
						} 
						catch ( NumberFormatException nex ) {
							// Just ignore all in case of problems
						}
					}
				} // if valid chunks
			} // if there are chunks
			
			// Is it a single number?
			else if ( NumberUtils.isNumber ( pvalStr ) )
			{
				try 
				{
					double v = Double.parseDouble ( pvalStr );

					// TODO: We need fixes to be able to accommodate bigger numbers in Oracle
					if ( Math.abs ( v ) < 10E125d ) dataItem = new NumberItem ( v );
				}
				catch ( NumberFormatException nex ) {
					// Just ignore all in case of problems
				}
			}
			else if ( pval.getUnit () == null )
			{
				// Or maybe a single date?
				
				Date date = null;
				
				// TODO: factorise these constants
				try {
					date = DateUtils.parseDate ( pvalStr, 
						"dd'/'MM'/'yyyy", "dd'/'MM'/'yyyy HH:mm:ss", "dd'/'MM'/'yyyy HH:mm", 
						"dd'-'MM'-'yyyy", "dd'-'MM'-'yyyy HH:mm:ss", "dd'-'MM'-'yyyy HH:mm",
						"yyyyMMdd", "yyyyMMdd'-'HHmmss", "yyyyMMdd'-'HHmm"  
					);
				}
				catch ( ParseException dex ) {
					// Just ignore all in case of problems
				}
				
				if ( date != null )
				{
					Calendar cal = DateUtils.toCalendar ( date );
					int year = cal.get ( Calendar.YEAR );
					if ( ( year >= -15402 && year <= 9999 ) )
						// This is an Oracle limitation
						dataItem = new DateItem ( date );
				}

			}
			
			if ( dataItem == null ) return false; 
	
			// annotate the new DI with origin and provenance
			dataItem.setSourceText ( pvalStr );
			dataItem.setType ( ANNOTATION_TYPE_MARKER );
			dataItem.setProvenance ( PropertyValAnnotationManager.PROVENANCE_MARKER );
			dataItem.setTimestamp ( new Date () );
	
			// Save in the memory store, for later persistence
			store.put ( DataItem.class, pvalStr, dataItem );
			
			return true;
		
		} // synchronized ( pvalStr )
	} // annotateData ()

}
