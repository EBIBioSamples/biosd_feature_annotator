package uk.ac.ebi.fg.biosd.annotator.olsclient.utils;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.*;

/**
 * Created by olgavrou on 21/04/2016.
 */
public class OLSWebServiceUtilsTest {

    private OLSWebServiceUtils olsWebServiceUtils;
    private String olsLocation;

    @Before
    public void setup(){
        olsWebServiceUtils = new OLSWebServiceUtils();
        olsLocation = "http://www.ebi.ac.uk/ols/beta";
    }

    @Test
    public void testGetCorrectOntologyPrefix(){
        assertEquals("Ontology NOT found", "NCBITaxon", olsWebServiceUtils.getOntology(olsLocation, "ncbi taxonomy"));

        assertEquals("Ontology NOT found", "EFO", olsWebServiceUtils.getOntology(olsLocation, "http://www.ebi.ac.uk/efo/EFO_0000001"));

        assertNull("Ontology Acronym not given", olsWebServiceUtils.getOntology(olsLocation, null));

        assertNull("Rubbish ontology acronym", olsWebServiceUtils.getOntology(olsLocation, "RUBISSHH"));

    }

    @Test(expected = Exception.class)
    public void testOLSNullLocationThrowsException(){
        assertNull("OLS location not given", olsWebServiceUtils.getOntology(null, "efo"));
    }

    @Test
    public void testSearchOLS(){
        Map<String, String> params = new HashMap<>();
        params.put("type","ontology");

        String response = olsWebServiceUtils.invokeOLS(olsLocation, "efo",params,true);
        assertNotNull("Specified exact twice", olsWebServiceUtils.invokeOLS(olsLocation, "efo",params,true));
        assertNotNull("Specified exact twice", olsWebServiceUtils.invokeOLS(olsLocation, "efo",params,true));

    }
}
