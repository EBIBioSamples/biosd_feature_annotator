package uk.ac.ebi.fg.biosd.annotator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.bioportal.webservice.client.BioportalClient;
import uk.ac.ebi.fg.biosd.annotator.datadiscover.NumericalDataAnnotator;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.BioSDCachedOntoTermDiscoverer;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.BioSDOntoDiscoveringCache;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.BioportalUnitDiscoverer;
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
import uk.ac.ebi.onto_discovery.api.OntologyTermDiscoverer;
import uk.ac.ebi.onto_discovery.bioportal.BioportalOntoTermDiscoverer;

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
	
	/**
	 * The service to be used to discover ontology terms from {@link ExperimentalPropertyValue value/type} pairs about
	 * sample attributes. Values are: 'bioportal', 'zooma'.
	 * 
	 */
	public static final String ONTO_DISCOVERER_PROP_NAME = AnnotatorResources.PROP_NAME_PREFIX + "ontoDiscoverer";


	/**
	 * We give priority to these
	 */
	public static final String BIOPORTAL_ONTOLOGIES = 
	  "EFO,UBERON,CL,CHEBI,BTO,GO,OBI,MESH,FMA,IAO,HP,BAO,MA,ICD10CM,NIFSTD,DOID,IDO,LOINC,OMIM,SIO,CLO,FHHO";
	
	/**
	 * Used for {@link AnnotationProvenance}, to mark that an annotation comes from this annotation tool.
	 */
	public final static String PROVENANCE_MARKER = "BioSD Feature Annotation Tool";
	
	protected final NumericalDataAnnotator numAnnotator;
	protected final OntoResolverAndAnnotator ontoResolver;
	protected final OntoDiscoveryAndAnnotator ontoDiscoverer;

	protected Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	
	protected PropertyValAnnotationManager ( AnnotatorResources resources )
	{
		ontoResolver = new OntoResolverAndAnnotator ();
		
		OntologyTermDiscoverer baseDiscoverer = null, unitBaseDiscoverer = null;

		String ontoDiscovererProp = System.getProperty ( ONTO_DISCOVERER_PROP_NAME, "zooma" );
		
		if ( "zooma".equalsIgnoreCase ( ontoDiscovererProp ) )
		{
			AbstractZOOMASearch zoomaClient = resources.getZoomaClient (); 
			baseDiscoverer = new ZoomaOntoTermDiscoverer ( zoomaClient );
			unitBaseDiscoverer = new ZoomaOntoTermDiscoverer ( new ZOOMAUnitSearch (	zoomaClient	) );
		}
		else if ( "bioportal".equalsIgnoreCase ( ontoDiscovererProp ) )
		{
			BioportalClient bpclient = resources.getBioportalClient ();
			baseDiscoverer = new BioportalOntoTermDiscoverer ( bpclient );
			((BioportalOntoTermDiscoverer) baseDiscoverer).setPreferredOntologies ( BIOPORTAL_ONTOLOGIES );
			unitBaseDiscoverer = new BioportalUnitDiscoverer (  bpclient );
		}
		else throw new IllegalArgumentException ( String.format ( 
			"Bad value '%s' for the property '%s'", ontoDiscovererProp, ONTO_DISCOVERER_PROP_NAME 
		));
		
		log.info ( "Ontology Discoverer set to {}", ontoDiscovererProp );
		
		numAnnotator = new NumericalDataAnnotator (
			new BioSDCachedOntoTermDiscoverer ( // 1st level, Memory Cache
				new CachedOntoTermDiscoverer ( // 2nd level, BioSD cache
					unitBaseDiscoverer,
					new BioSDOntoDiscoveringCache ()
				),
				new OntoTermDiscoveryStoreCache ()
			)
		);
		
		ontoDiscoverer = new OntoDiscoveryAndAnnotator (
			new BioSDCachedOntoTermDiscoverer ( // 1st level, Memory Cache
				new CachedOntoTermDiscoverer ( // 2nd level, BioSD cache
					baseDiscoverer,
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
