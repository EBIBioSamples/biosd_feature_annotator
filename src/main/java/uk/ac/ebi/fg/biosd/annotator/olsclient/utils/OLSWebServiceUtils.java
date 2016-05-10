package uk.ac.ebi.fg.biosd.annotator.olsclient.utils;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by olgavrou on 20/04/2016.
 */
public class OLSWebServiceUtils {

    /*
    From an ontologyAcronym it searches OLS for a correct ontology prefix
    Searches first for an exact match, and if none found searches without exact match
    e.g. NCBI Taxonomy will return NCBITaxon
    @olsLocation: the ols uri
    @ontoAcronym: the proposed ontology prefix
    returns: the correct ontology prefix, if any
     */
    public String getOntology(String olsLocation, String ontoAcronym){


        Map<String, String> params = new HashMap<>();
        params.put("type", "ontology");
        String response = searchOLS(olsLocation, ontoAcronym, params, true); // first search for exact match

        OLSResponseParser olsParser = new OLSResponseParser();
        JsonNode doc = olsParser.getFirstDoc(response);
        if (doc == null){ // not found, now search withought exact
            response = searchOLS(olsLocation, ontoAcronym, params, false); // first search for exact match
            doc = olsParser.getFirstDoc(response);
        }

        JsonNode ontologyPrefix =  olsParser.getFieldFromDoc(doc, "ontology_prefix");
        if (ontologyPrefix != null){
            return ontologyPrefix.asText();
        }
        return null;
    }

    /*
    returns the response in searching an ontology class given an ontology prefix and an ontology accession
    @olsLocation: the ols uri
    @ontologyPrefix: the ontology name in which we will be looking into, e.g. EFO
    @accession: accession will be the general query we will search for
    returns: the json response
     */
    public String getOntologyClass(String olsLocation, String ontologyPrefix, String q, Map<String, String> params, boolean exact) {

        String response = null;
        Map<String, String> parameters = new HashMap<>();
        if (params != null){
            parameters.putAll(params);
        }
        if (ontologyPrefix != null) {
            parameters.put("ontology", ontologyPrefix.toLowerCase());
        }

        response = searchOLS(olsLocation, q, parameters, exact);

        return response;
    }


    /*
    Searches OLS for a query at /api/search
    @olsLocation: the ols uri
    @q: the basic query
    @map: any other parameters you want to specify
    @exact: true if you want an exact search
    returns OLS's response
     */
    public String searchOLS(String olsLocation, String q, Map map, boolean exact){

        String response = null;
        Map <String, String> params = new HashMap<>();
        params.put("q",q);
        params.put("exact", String.valueOf(exact));
        params.putAll(map);
        HttpRequestHandler requestHandler = new HttpRequestHandler();

        try {
            response = requestHandler.executeHttpGet(olsLocation + "/api/search", params);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return  response;
    }

}
