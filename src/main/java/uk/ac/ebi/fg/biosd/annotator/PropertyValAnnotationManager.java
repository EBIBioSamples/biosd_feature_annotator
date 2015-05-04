package uk.ac.ebi.fg.biosd.annotator;

import java.util.Set;

import javax.persistence.EntityManager;

import uk.ac.ebi.fg.biosd.annotator.datadiscover.NumericalDataAnnotator;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.BioSDCachedOntoTermDiscoverer;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.BioSDOntoDiscoveringCache;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.ExtendedDiscoveredTerm;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.OntoDiscoveryAndAnnotator;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.OntoResolverAndAnnotator;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.ZOOMAUnitSearch;
import uk.ac.ebi.fg.biosd.annotator.persistence.SynchronizedStore;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.MemoryStore;
import uk.ac.ebi.fg.biosd.sampletab.parser.object_normalization.normalizers.expgraph.properties.PropertyValueNormalizer;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.expgraph.properties.Unit;
import uk.ac.ebi.fg.core_model.expgraph.properties.UnitDimension;
import uk.ac.ebi.fg.core_model.expgraph.properties.dataitems.DataItem;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AnnotatableDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.FreeTextTerm;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.toplevel.Annotatable;
import uk.ac.ebi.fg.core_model.toplevel.Annotation;
import uk.ac.ebi.fg.core_model.toplevel.AnnotationProvenance;
import uk.ac.ebi.fg.core_model.toplevel.Identifiable;
import uk.ac.ebi.fg.core_model.xref.ReferenceSource;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.CachedOntoTermDiscoverer;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntoTermDiscoveryMemCache;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.ZoomaOntoTermDiscoverer;
import uk.ac.ebi.utils.reflection.ReflectionUtils;

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
	private final PropertyValueNormalizer propValNormalizer; 
	
	/**
	 * Used for {@link AnnotationProvenance}, to mark that an annotation comes from this annotation tool.
	 */
	public final static String PROVENANCE_MARKER = "BioSD Feature Annotation Tool";
	
	PropertyValAnnotationManager ( float zoomaThreesholdScore, AnnotatorResources annRes )
	{
		propValNormalizer = new PropertyValueNormalizer ( annRes.getStore () );

		ontoResolver = new OntoResolverAndAnnotator ();
		
		numAnnotator = new NumericalDataAnnotator (
			new BioSDCachedOntoTermDiscoverer ( // 1st level, Memory Cache
				new CachedOntoTermDiscoverer ( // 2nd level, BioSD cache
					new ZoomaOntoTermDiscoverer ( 
						new ZOOMAUnitSearch ( annRes.getZoomaClient () ), 
						zoomaThreesholdScore 
					),
					new BioSDOntoDiscoveringCache ()
				),
				new OntoTermDiscoveryMemCache ( annRes.getOntoTerms () )
			)
		);
		
		ontoDiscoverer = new OntoDiscoveryAndAnnotator (
			new BioSDCachedOntoTermDiscoverer ( // 1st level, Memory Cache
				new CachedOntoTermDiscoverer ( // 2nd level, BioSD cache
					new ZoomaOntoTermDiscoverer ( annRes.getZoomaClient (), zoomaThreesholdScore ),
					new BioSDOntoDiscoveringCache ()
				),
				new OntoTermDiscoveryMemCache ( annRes.getOntoTerms () )
			)
		);
	}

	/**
	 * Defaults to a score threshold of 80.
	 */
	PropertyValAnnotationManager ( AnnotatorResources annRes )
	{
		this ( 80f, annRes );
	}

	/**
	 * Call different types of annotators and link the computed results to the property value. pvalId is the 
	 * property #ID in the BioSD database.
	 *  
	 */
	@SuppressWarnings ( { "rawtypes", "unchecked" } )
	public boolean annotate ( long pvalId )
	{
		EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
		
		try
		{
			AnnotatableDAO<ExperimentalPropertyValue> pvdao = new AnnotatableDAO<> ( ExperimentalPropertyValue.class, em );
			ExperimentalPropertyValue<ExperimentalPropertyType> pval = pvdao.find ( pvalId );
			
			initializeLazy ( (ExperimentalPropertyValue) pval );
			
			ontoResolver.annotate ( pval  );
			boolean isNumberOrDate = numAnnotator.annotate ( pval );
			ontoDiscoverer.annotate ( pval, isNumberOrDate );
	
			// Normalise new ontology entries and annotations
			Long oldPvId = pval.getId ();
			// Enables the procedures in the normalizer
			ReflectionUtils.invoke ( pval, Identifiable.class, "setId", new Class<?>[] { Long.class }, (Long) null );
			propValNormalizer.normalize ( pval );
			ReflectionUtils.invoke ( pval, Identifiable.class, "setId", new Class<?>[] { Long.class }, oldPvId );
			
			// Save this way to memory, so that the persister is later able to fetch all instances of ExperimentalPropertyValue
			MemoryStore store = ((SynchronizedStore) AnnotatorResources.getInstance ().getStore ()).getBase ();
			synchronized ( store ) {
				store.put ( ExperimentalPropertyValue.class, pval.getId ().toString (), pval );
			}
	
			return true;
		}
		finally {
			if ( em.isOpen () ) em.close ();
		}
	}
	
	/**
	 * Loads all lazy collections, which is needed here, because the object will be detached by the current session,
	 * and its collections will be used elsewhere, and JPA/Hibernate bloody suck and I hate them more than ever. 
	 */
	public static void initializeLazy ( ExperimentalPropertyValue<?> pval )
	{
		initializeLazy ( (FreeTextTerm) pval );
		
		ExperimentalPropertyType type = pval.getType ();
		if ( type != null ) initializeLazy ( (FreeTextTerm) type );
		
		Unit u = pval.getUnit ();
		if ( u != null ) 
		{
			initializeLazy ( (FreeTextTerm) u );
			
			UnitDimension dim = u.getDimension ();
			if ( dim != null ) initializeLazy ( (FreeTextTerm) dim );
		}
		
		for ( DataItem di: pval.getDataItems () )
			initializeLazy ( (Annotatable) di );
		
	}
	
	/**
	 * @see #initializeLazy(ExperimentalPropertyValue).
	 */
	public static void initializeLazy ( FreeTextTerm t )
	{
		initializeLazy ( (Annotatable) t );
		Set<OntologyEntry> oes = t.getOntologyTerms ();
		if ( oes == null ) return;
		
		for ( OntologyEntry oe: oes ) 
		{
			initializeLazy ( (Annotatable) oe );
			ReferenceSource src = oe.getSource ();
			if ( src != null ) initializeLazy ( (Annotatable) src );
		}
	}
	
	/**
	 * @see #initializeLazy(ExperimentalPropertyValue).
	 */
	public static void initializeLazy ( Annotatable a )
	{
		Set<Annotation> anns = a.getAnnotations ();
		if ( anns == null ) return;
		
		for ( Annotation ann: anns ) {
			ann.getType ();
			ann.getProvenance ();
		}
	}
}
