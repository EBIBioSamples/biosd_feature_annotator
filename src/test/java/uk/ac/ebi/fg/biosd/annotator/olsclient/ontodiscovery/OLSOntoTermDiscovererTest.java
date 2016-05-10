package uk.ac.ebi.fg.biosd.annotator.olsclient.ontodiscovery;

import org.junit.Before;
import org.junit.Test;
import uk.ac.ebi.onto_discovery.api.OntologyTermDiscoverer;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static junit.framework.Assert.*;

/**
 * Created by olgavrou on 26/04/2016.
 */
public class OLSOntoTermDiscovererTest {


    private OLSOntoTermDiscoverer discoverer;

    @Before
    public void setup(){
        //Set general System properties
        Properties properties = new Properties( System.getProperties() );
        try {
            properties.load ( getClass () .getClassLoader ().getResourceAsStream ("annotator.properties"));
        } catch (IOException e) {
            //handle differently if annotation properties are not vital to running the annotator
            throw new RuntimeException ( "Annotator Properties not found" );
        }
        System.setProperties ( properties );

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
