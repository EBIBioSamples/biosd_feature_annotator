package uk.ac.ebi.fg.biosd.annotator;

import uk.ac.ebi.fg.biosd.annotator.datadiscover.NumericalDataAnnotator;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.BioSDCachedOntoTermDiscoverer;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.BioSDOntoDiscoveringCache;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.OntoDiscoveryAndAnnotator;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.OntoResolverAndAnnotator;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.OntoTermDiscoveryStoreCache;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.ZOOMAUnitSearch;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.toplevel.AnnotationProvenance;
import uk.ac.ebi.fgpt.zooma.search.AbstractZOOMASearch;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.CachedOntoTermDiscoverer;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.ZoomaOntoTermDiscoverer;

/**
 * This annotates a {@link ExperimentalPropertyValue} with ontology entries returned by {@link OntologyTermDiscoverer},
 * which in turn uses a memory cache and {@link BioSDOntoDiscoveringCache}. Details about how this happens are explained
 * in {@link ExtendedDiscoveredTerm}.
 * 
 * TODO: This also annotates a property value with information extracted from it about 1) explicity ontology entries
 * (which are checked via Bioportal) 2) numeric/date values, including ranges, and units (Unit Ontology + Bioportal
 * are used for this).
 * 
 * <dl><dt>date</dt><dd>1 Sep 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class PropertyValAnnotationManager
{	
	private final NumericalDataAnnotator numAnnotator;
	private final OntoResolverAndAnnotator ontoResolver;
	private final OntoDiscoveryAndAnnotator ontoDiscoverer;
	
	/**
	 * Used for {@link AnnotationProvenance}, to mark that an annotation comes from this annotation tool.
	 */
	public final static String PROVENANCE_MARKER = "BioSD Feature Annotation Tool";
	
	PropertyValAnnotationManager ( float zoomaThresholdScore, AbstractZOOMASearch zoomaClient )
	{
		ontoResolver = new OntoResolverAndAnnotator ();
		
		numAnnotator = new NumericalDataAnnotator (
			new BioSDCachedOntoTermDiscoverer ( // 1st level, Memory Cache
				new CachedOntoTermDiscoverer ( // 2nd level, BioSD cache
					new ZoomaOntoTermDiscoverer ( 
						new ZOOMAUnitSearch ( zoomaClient ), zoomaThresholdScore 
					),
					new BioSDOntoDiscoveringCache ()
				),
				new OntoTermDiscoveryStoreCache ()
			)
		);
		
		ontoDiscoverer = new OntoDiscoveryAndAnnotator (
			new BioSDCachedOntoTermDiscoverer ( // 1st level, Memory Cache
				new CachedOntoTermDiscoverer ( // 2nd level, BioSD cache
					new ZoomaOntoTermDiscoverer ( zoomaClient, zoomaThresholdScore ),
					new BioSDOntoDiscoveringCache ()
				),
				new OntoTermDiscoveryStoreCache ()
			)
		);
	}

	/**
	 * Defaults to a score threshold of 80.
	 */
	PropertyValAnnotationManager ( AbstractZOOMASearch annRes )
	{
		this ( 80f, annRes );
	}

	/**
	 * Call different types of annotators and link the computed results to the property value. pvalId is the 
	 * property #ID in the BioSD database.
	 *  
	 */
	public void annotate ( ExperimentalPropertyValue<ExperimentalPropertyType> pv )
	{
		boolean isNumberOrDate = numAnnotator.annotate ( pv );
		ontoDiscoverer.annotate ( pv, isNumberOrDate );
		ontoResolver.annotate ( pv );
	}
	
}
