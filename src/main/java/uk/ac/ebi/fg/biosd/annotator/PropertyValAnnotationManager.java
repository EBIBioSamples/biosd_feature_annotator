package uk.ac.ebi.fg.biosd.annotator;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import uk.ac.ebi.fg.biosd.annotator.datadiscover.NumericalDataAnnotator;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.BioSDCachedOntoTermDiscoverer;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.BioSDOntoDiscoveringCache;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.ExtendedDiscoveredTerm;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.OntoDiscoveryAndAnnotator;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.OntoTermResolverAndAnnotator;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.ZOOMAUnitSearch;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AnnotatableDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.toplevel.AnnotationProvenance;
import uk.ac.ebi.fgpt.zooma.search.StatsZOOMASearchFilter;
import uk.ac.ebi.fgpt.zooma.search.ZOOMASearchClient;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.CachedOntoTermDiscoverer;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntoTermDiscoveryMemCache;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer.DiscoveredTerm;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.ZoomaOntoTermDiscoverer;
import uk.ac.ebi.utils.memory.SimpleCache;

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
	private final OntoTermResolverAndAnnotator ontoResolver;
	private final OntoDiscoveryAndAnnotator ontoDiscoverer;
	
	/**
	 * Used for {@link AnnotationProvenance}, to mark that an annotation comes from this annotation tool.
	 */
	public final static String PROVENANCE_MARKER = "BioSD Feature Annotation Tool";
	
	public PropertyValAnnotationManager ( float zoomaThreesholdScore )
	{
		ontoResolver = new OntoTermResolverAndAnnotator ();
		
		numAnnotator = new NumericalDataAnnotator (
			new BioSDCachedOntoTermDiscoverer ( // 1st level, BioSD cache
				new CachedOntoTermDiscoverer ( // 2nd level, memory cache
					new ZoomaOntoTermDiscoverer ( 
						new ZOOMAUnitSearch ( new StatsZOOMASearchFilter ( new ZOOMASearchClient () ) ), 
						zoomaThreesholdScore 
					),
					new OntoTermDiscoveryMemCache ( new SimpleCache<String, List<DiscoveredTerm>> ( 10000 ) )
				),
				new BioSDOntoDiscoveringCache ()
			)
		);
		
		ontoDiscoverer = new OntoDiscoveryAndAnnotator (
			new BioSDCachedOntoTermDiscoverer ( // 1st level, BioSD cache
				new CachedOntoTermDiscoverer ( // 2nd level, memory cache
					new ZoomaOntoTermDiscoverer ( new StatsZOOMASearchFilter ( new ZOOMASearchClient () ), zoomaThreesholdScore )
				),
				new BioSDOntoDiscoveringCache ()
			)
		);
	}

	/**
	 * Defaults to a score threshold of 80.
	 */
	public PropertyValAnnotationManager ()
	{
		this ( 80f );
	}

	/**
	 * Call different types of annotators and link the computed results to the property value. pvalId is the 
	 * property #ID in the BioSD database.
	 *  
	 */
	@SuppressWarnings ( { "rawtypes", "unchecked" } )
	public boolean annotate ( long pvalId )
	{
		EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
		EntityManager em = emf.createEntityManager ();
		
		try
		{
			AnnotatableDAO<ExperimentalPropertyValue> pvdao = new AnnotatableDAO<> ( ExperimentalPropertyValue.class, em );
			EntityTransaction tx = em.getTransaction ();
			tx.begin ();
			ExperimentalPropertyValue<ExperimentalPropertyType> pval = pvdao.find ( pvalId );
			tx.commit ();
			
			ontoResolver.annotate ( pval, em );
			boolean isNumberOrDate = numAnnotator.annotate ( pval, em );
			ontoDiscoverer.annotate ( pval, isNumberOrDate, em );			
		}
		finally {
			if ( em.isOpen () ) em.close ();
		}

		return true;
	}	

}
