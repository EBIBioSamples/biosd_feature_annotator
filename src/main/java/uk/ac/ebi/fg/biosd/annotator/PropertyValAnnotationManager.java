package uk.ac.ebi.fg.biosd.annotator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.annotator.datadiscover.NumericalDataAnnotator;
import uk.ac.ebi.fg.biosd.annotator.olsclient.client.OLSClient;
import uk.ac.ebi.fg.biosd.annotator.olsclient.ontodiscovery.OLSOntoTermDiscoverer;
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
import uk.ac.ebi.onto_discovery.api.OntologyTermDiscoverer;

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
	
	protected final NumericalDataAnnotator ZoomaNumAnnotator;
	protected final NumericalDataAnnotator OLSNumAnnotator;
	protected final OntoResolverAndAnnotator ontoResolver;
	protected final OntoDiscoveryAndAnnotator zoomaOntoDiscoverer;
	protected final OntoDiscoveryAndAnnotator olsOntoDiscoverer;


	protected Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	
	protected PropertyValAnnotationManager ( AnnotatorResources resources )
	{
		ontoResolver = new OntoResolverAndAnnotator ();
		
		OntologyTermDiscoverer zoomaBaseDiscoverer = null, zoomaUnitBaseDiscoverer = null;
		OntologyTermDiscoverer olsBaseDiscoverer = null, olsUnitBaseDiscoverer = null;


		AbstractZOOMASearch zoomaClient = resources.getZoomaClient ();
		zoomaBaseDiscoverer = new ZoomaOntoTermDiscoverer ( zoomaClient );
		zoomaUnitBaseDiscoverer = new ZoomaOntoTermDiscoverer ( new ZOOMAUnitSearch (	zoomaClient	) );

		OLSClient olsClient = resources.getOLSClient ();
		olsBaseDiscoverer = new OLSOntoTermDiscoverer( olsClient );

		ZoomaNumAnnotator = new NumericalDataAnnotator (
			new BioSDCachedOntoTermDiscoverer ( // 1st level, Memory Cache
				new CachedOntoTermDiscoverer( // 2nd level, BioSD cache
					zoomaUnitBaseDiscoverer,
					new BioSDOntoDiscoveringCache ()
				),
				new OntoTermDiscoveryStoreCache ("ZOOMA")
			)
		);

		OLSNumAnnotator = new NumericalDataAnnotator (
				new BioSDCachedOntoTermDiscoverer ( // 1st level, Memory Cache
						new CachedOntoTermDiscoverer ( // 2nd level, BioSD cache
								olsBaseDiscoverer,
								new BioSDOntoDiscoveringCache ()
						),
						new OntoTermDiscoveryStoreCache ("OLS")
				)
		);

		zoomaOntoDiscoverer = new OntoDiscoveryAndAnnotator (
			new BioSDCachedOntoTermDiscoverer ( // 1st level, Memory Cache
				new CachedOntoTermDiscoverer ( // 2nd level, BioSD cache
					zoomaBaseDiscoverer,
					new BioSDOntoDiscoveringCache ()
				),
				new OntoTermDiscoveryStoreCache ("ZOOMA")
			)
		);

		olsOntoDiscoverer = new OntoDiscoveryAndAnnotator (
				new BioSDCachedOntoTermDiscoverer ( // 1st level, Memory Cache
						new CachedOntoTermDiscoverer ( // 2nd level, BioSD cache
								olsBaseDiscoverer,
								new BioSDOntoDiscoveringCache ()
						),
						new OntoTermDiscoveryStoreCache ("OLS")
				)
		);
	}

	/*
	Annotate attribute values (text)
	First try with zooma, and if no HIGH hits returned then
	try with OLS
	 */
	public void textAnnotation(ExperimentalPropertyValue<ExperimentalPropertyType> pv){
		boolean isNumberOrDate = ZoomaNumAnnotator.annotate ( pv );
		if (! zoomaOntoDiscoverer.tryToAnnotate ( pv, isNumberOrDate )){ //if none where annotated from zooma, look into ols
			 isNumberOrDate = OLSNumAnnotator.annotate ( pv );
		olsOntoDiscoverer.tryToAnnotate(pv, isNumberOrDate);
		}

	}

	/*
	Resolve suggested ontology terms through OLS
	 */
	public void ontologyTermAnnotation(ExperimentalPropertyValue<ExperimentalPropertyType> pv){
		ontoResolver.annotate ( pv );
	}

	/**
	 * Call different types of annotators and link the computed results to the property value. 
	 */
	public void annotate ( ExperimentalPropertyValue<ExperimentalPropertyType> pv )
	{
		textAnnotation(pv);
		ontologyTermAnnotation(pv);
	}
}
