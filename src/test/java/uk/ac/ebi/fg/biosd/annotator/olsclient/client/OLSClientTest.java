package uk.ac.ebi.fg.biosd.annotator.olsclient.client;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.*;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import uk.ac.ebi.bioportal.webservice.model.OntologyClass;
import uk.ac.ebi.bioportal.webservice.model.TextAnnotation;

/**
 * Created by olgavrou on 21/04/2016.
 */
public class OLSClientTest {

    private OLSClient olsClient;
    @Before
    public void setup() {
        this.olsClient = new OLSClient();
    }

    @Test
    public void testGetOntologyClass(){
        OntologyClass cls1 = olsClient.getOntologyClass ( "EFO", "EFO_0000270" );
        assertEquals ( "Bad prefLabel!", "asthma", cls1.getPreferredLabel () );

        OntologyClass cls2 = olsClient.getOntologyClass ( "GO", "GO_1902084" );
        assertTrue ( "Bad prefLabel!", cls2.getPreferredLabel ().contains ( "fumagillin metabolic process" ));

        OntologyClass cls3 = olsClient.getOntologyClass("NCBI Taxonomy","3750");
        assertEquals("Bad prefLabel!", "Malus domestica", cls3.getPreferredLabel());

        // Use a URI straight
        assertNotNull ( "URI fetching doesn't work!", olsClient.getOntologyClass ( "EFO", "http://www.ebi.ac.uk/efo/EFO_0000001" ) );

        assertNull ( "Should return null term!", olsClient.getOntologyClass ( "RUBBISH123", "FOO-456" ) );
        assertNull ( "Should return null term!", olsClient.getOntologyClass ( "EFO", "BAD-ACC" ) );

        // Use a URI, without ontology acronym, this works only for some known ontologies
        OntologyClass nullOntoClass = olsClient.getOntologyClass ( null, "http://www.ebi.ac.uk/efo/EFO_0000571" );
        assertNotNull ( "Null result for URI-only query!", nullOntoClass );
        System.out.println( "Result for URI-only query: {}" +  nullOntoClass );
        assertEquals ( "URI-only query returns wrong ontology!", "EFO", nullOntoClass.getOntologyAcronym () );

        nullOntoClass = olsClient.getOntologyClass ( null, "http://purl.obolibrary.org/obo/OBI_0001274" );
        assertNotNull ( "Null result for URI-only query!", nullOntoClass );
        System.out.println( "Result for URI-only query: {}" +  nullOntoClass );
        assertEquals ( "URI-only query returns wrong ontology!", "OBI", nullOntoClass.getOntologyAcronym () );

        // This is a special case
        OntologyClass omimClass = olsClient.getOntologyClass ( null, "http://omim.org/entry/233420" );
        assertNotNull ( "OMIM term not found!", omimClass );
        System.out.println( "Result for OMIM (straight URI): {}" + omimClass );

        //TODO: omim is not in ols, this will be weird
        /*omimClass = olsClient.getOntologyClass ( "OMIM", "http://omim.org/entry/233420" );
        assertNotNull ( "OMIM term not found!", omimClass );
        System.out.println ( "Result for OMIM (acronym + URI): {}" + omimClass );*/
    }

    @Test
    public void testGetTextAnnotations(){

        TextAnnotation tas = olsClient.getTextAnnotations ( "homo sapiens", null );
        assertNotNull ( "No text annotation from OLS annotator!", tas );

        boolean found = false;

        found = found || "http://purl.obolibrary.org/obo/NCBITaxon_9606".equals ( tas.getAnnotatedClass ().getClassIri () );


        assertTrue ( "the text annotator doesn't return NCBITaxon_9606!", found );

        tas = olsClient.getTextAnnotations ( "mus musculus", null);
        assertNotNull ( "No text annotation from OLS annotator!", tas );

        found = false;

        found = found || "http://purl.obolibrary.org/obo/NCBITaxon_10090".equals ( tas.getAnnotatedClass ().getClassIri () );

        assertTrue ( "the text annotator doesn't return NCBITaxon_10090!", found );

        tas = olsClient.getTextAnnotations ( "jibberish", null);
        assertNull ( "Found jibberish match!", tas );
    }


}
