package uk.ac.ebi.fg.biosd.annotator.olsclient.ontodiscovery;

import org.junit.Before;
import org.junit.Test;
import uk.ac.ebi.onto_discovery.api.OntologyTermDiscoverer;

import java.util.List;

import static junit.framework.Assert.*;

/**
 * Created by olgavrou on 26/04/2016.
 */
public class OLSOntoTermDiscovererTest {


    private OLSOntoTermDiscoverer discoverer;

    @Before
    public void setup(){
        this.discoverer = new OLSOntoTermDiscoverer();
    }
    @Test
    public void testGetOntologyTerms(){

        List<OntologyTermDiscoverer.DiscoveredTerm> dterms = discoverer.getOntologyTerms ( "homo sapiens", null );

        assertNotNull ( "the onto discoverer returns null!", dterms );
        assertFalse ( "the onto discoverer returns empty!", dterms.isEmpty () );

        boolean found = false;

        found = found || "http://purl.obolibrary.org/obo/NCBITaxon_9606".equals ( dterms.get(0).getIri () );

        assertEquals("Annotator returned more than one mapping!", 1, dterms.size());

        assertTrue ( "onto term discoverer doesn't return NCBITaxon_9606!", found );

        dterms = discoverer.getOntologyTerms ( "mus musculus", null );

        found = false;

        found = found || "http://purl.obolibrary.org/obo/NCBITaxon_10090".equals ( dterms.get(0).getIri () );


        assertTrue ( "onto term discoverer doesn't return NCBITaxon_10090!", found );

        assertEquals("Annotator returned more than one mapping!", 1, dterms.size());

        //Assert that it doesn't return rubbish
        dterms = discoverer.getOntologyTerms ( "rubbish", null );
        assertEquals("Found a mapping for rubbish input!", 0, dterms.size());

        dterms = discoverer.getOntologyTerms ( null, null );
        assertEquals("Found a mapping for rubbish input!", 0, dterms.size());

        dterms = discoverer.getOntologyTerms ( null, "rubbish" );
        assertEquals("Found a mapping for rubbish input!", 0, dterms.size());

    }
}
