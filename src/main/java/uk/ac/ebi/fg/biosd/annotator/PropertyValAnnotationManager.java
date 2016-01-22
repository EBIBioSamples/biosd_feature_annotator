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
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.ZoomaOntoTermDiscoverer;
import uk.ac.ebi.onto_discovery.api.CachedOntoTermDiscoverer;

/**
 * Coordinates the task of computing several annotations for a single {@link ExperimentalPropertyValue}, including
 * ontology annotations and numerical value extractions.  
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>1 Oct 2015</dd>
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
	
	PropertyValAnnotationManager ( AbstractZOOMASearch zoomaClient )
	{
		ontoResolver = new OntoResolverAndAnnotator ();
		
		numAnnotator = new NumericalDataAnnotator (
			new BioSDCachedOntoTermDiscoverer ( // 1st level, Memory Cache
				new CachedOntoTermDiscoverer ( // 2nd level, BioSD cache
					new ZoomaOntoTermDiscoverer ( 
						new ZOOMAUnitSearch ( zoomaClient )
					),
					new BioSDOntoDiscoveringCache ()
				),
				new OntoTermDiscoveryStoreCache ()
			)
		);
		
		ontoDiscoverer = new OntoDiscoveryAndAnnotator (
			new BioSDCachedOntoTermDiscoverer ( // 1st level, Memory Cache
				new CachedOntoTermDiscoverer ( // 2nd level, BioSD cache
					new ZoomaOntoTermDiscoverer ( zoomaClient ),
					new BioSDOntoDiscoveringCache ()
				),
				new OntoTermDiscoveryStoreCache ()
			)
		);
	}


	/**
	 * Call different types of annotators and link the computed results to the property value. 
	 */
	public void annotate ( ExperimentalPropertyValue<ExperimentalPropertyType> pv )
	{
		boolean isNumberOrDate = numAnnotator.annotate ( pv );
		ontoDiscoverer.annotate ( pv, isNumberOrDate );
		ontoResolver.annotate ( pv );
	}	
}
