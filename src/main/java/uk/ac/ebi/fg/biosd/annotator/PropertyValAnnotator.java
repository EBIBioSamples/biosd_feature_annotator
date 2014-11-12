package uk.ac.ebi.fg.biosd.annotator;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import org.apache.commons.lang.StringUtils;

import uk.ac.ebi.fg.biosd.annotator.ontodiscover.BioSDOntoDiscoveringCache;
import uk.ac.ebi.fg.biosd.annotator.ontodiscover.ExtendedDiscoveredTerm;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AnnotatableDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.CachedOntoTermDiscoverer;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntoTermDiscoveryMemCache;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer.DiscoveredTerm;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.ZoomaOntoTermDiscoverer;
import uk.ac.ebi.utils.regex.RegEx;

/**
 * TODO: Comment me!
 *
 * <dl><dt>date</dt><dd>1 Sep 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class PropertyValAnnotator
{
	private final static RegEx COMMENT_RE = new RegEx ( "(Comment|Characteristic)\\s*\\[\\s*(.+)\\s*\\]", Pattern.CASE_INSENSITIVE );
	
	private OntologyTermDiscoverer ontoTermDiscoverer;
	
	
	public PropertyValAnnotator ( float zoomaThreesholdScore )
	{
		this.ontoTermDiscoverer = new CachedOntoTermDiscoverer (
			new CachedOntoTermDiscoverer ( 
				new ZoomaOntoTermDiscoverer ( zoomaThreesholdScore ), new BioSDOntoDiscoveringCache ()
			),
			new OntoTermDiscoveryMemCache ()
		); 
		
	}

	public PropertyValAnnotator ()
	{
		this ( 80f );
	}

	@SuppressWarnings ( "rawtypes" )
	public boolean annotate ( long pvalId )
	{
		EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
		EntityManager em = emf.createEntityManager ();
		
		AnnotatableDAO<ExperimentalPropertyValue> pvdao = new AnnotatableDAO<> ( ExperimentalPropertyValue.class, em );
		EntityTransaction tx = em.getTransaction ();
		tx.begin ();
		ExperimentalPropertyValue<?> pval = pvdao.find ( pvalId );
		tx.commit ();
		
		try
		{
			for ( DiscoveredTerm dterm: getOntoClassUris ( pval, false ) )
			{
					tx = em.getTransaction ();
					tx.begin ();
					OntologyEntry oe = ((ExtendedDiscoveredTerm) dterm).getOntologyTerm ();
					oe = em.merge ( oe );
					pval = em.merge ( pval );
					pval.addOntologyTerm ( oe );
					tx.commit ();
			}
		}
		finally {
			if ( em.isOpen () ) em.close ();
		}

		return true;
	}
	
	
	public List<DiscoveredTerm> getOntoClassUris ( ExperimentalPropertyValue<?> pval, boolean isNumberOrDate ) 
	{
		if ( pval == null ) return Collections.emptyList ();
		
		String pvalLabel = pval.getTermText ();
		
		ExperimentalPropertyType ptype = pval.getType ();
		if ( ptype == null ) return Collections.emptyList (); 
		
		String pvalTypeLabel = getExpPropTypeLabel ( ptype );
		if ( pvalTypeLabel == null ) return null;
		
		// Next, see if it is a number
		// Try to resolve the type instead of the value, use both value and type if it's not a number
		return ontoTermDiscoverer.getOntologyTermUris ( isNumberOrDate ? null : pvalLabel, pvalTypeLabel );
	}

	
	/**
	 * Extract the type label, considering things like Comment [ X ] (return 'X' in such cases).
	 */
	public static String getExpPropTypeLabel ( ExperimentalPropertyType ptype ) 
	{
		if ( ptype == null ) return null;
		String typeLabel = StringUtils.trimToNull ( ptype.getTermText () );
		if ( typeLabel == null ) return null;
		
		String[] chunks = COMMENT_RE.groups ( typeLabel );
		if ( chunks == null || chunks.length < 3 ) return typeLabel;
		return chunks [ 2 ];
	}
}
