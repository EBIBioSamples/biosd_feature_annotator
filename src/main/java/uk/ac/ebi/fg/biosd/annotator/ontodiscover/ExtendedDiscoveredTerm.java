package uk.ac.ebi.fg.biosd.annotator.ontodiscover;

import java.net.URI;

import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fgpt.zooma.search.ontodiscover.OntologyTermDiscoverer.DiscoveredTerm;

/**
 * TODO: Comment me!
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
	 * The BioSD ontology term this discovered term is mapped to. This is set by the machinery inside {@link BioSDOntoDiscoveringCache}. 
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
