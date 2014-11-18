package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import java.net.URI;

import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotator;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.toplevel.TextAnnotation;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer.DiscoveredTerm;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.ZoomaOntoTermDiscoverer;

/**
 * This is created by {@link BioSDOntoDiscoveringCache} and used by the {@link PropertyValAnnotator}.
 * In short, it's the usual {@link DiscoveredTerm ZOOMA discovered term}, enriched with the {@link OntologyEntry}
 * that was created for the ontology term URI that ZOOMA discovered for a string pair (this object, in turn, includes
 * a {@link TextAnnotation} tracking the ZOOMA provenance).
 * 
 * The {@link BioSDOntoDiscoveringCache} replace ordinary {@link DiscoveredTerm}s returned by {@link ZoomaOntoTermDiscoverer}
 * with instances of this extension, after having saved the ZOOMA result as {@link OntologyEntry}es in the BioSD database. 
 * 
 * This is returned to {@link PropertyValAnnotator}, which will take {@link #getOntologyTerm()} and will link it
 * to the {@link ExperimentalPropertyValue} that it's annotating via ZOOMA.
 *
 * <dl><dt>date</dt><dd>26 Aug 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class ExtendedDiscoveredTerm extends DiscoveredTerm
{
	private OntologyEntry ontologyTerm; 
	
	public ExtendedDiscoveredTerm ( URI uri, float score )
	{
		this ( uri, score, null );
	}

	public ExtendedDiscoveredTerm ( URI uri, float score, OntologyEntry oe )
	{
		super ( uri, score );
		this.setOntologyTerm ( oe );
	}

	/**
	 * The BioSD ontology term that this discovered term is mapped to. This is set by the machinery inside 
	 * {@link BioSDOntoDiscoveringCache}. 
	 */
	public OntologyEntry getOntologyTerm ()
	{
		return ontologyTerm;
	}

	void setOntologyTerm ( OntologyEntry ontologyTerm )
	{
		this.ontologyTerm = ontologyTerm;
	}
	
}
