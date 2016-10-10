package uk.ac.ebi.fg.biosd.annotator.olsclient.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;

/**
 * Created by olgavrou on 20/04/2016.
 */
public class OLSResponseParser {

    public JsonNode getDocs(String jsonResponse) {
        ObjectMapper mapper = new ObjectMapper();

        JsonNode jsonNode = null;
        try {
            jsonNode = mapper.readTree(jsonResponse);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        JsonNode response = jsonNode.get("response");
        if (response != null){
            ArrayNode docs = (ArrayNode) response.get("docs");
            if (docs != null && docs.size() != 0){
                return (JsonNode) docs;
            }
        }
        return null;
    }

    public JsonNode getFirstDoc(String jsonResponse) {

        ObjectMapper mapper = new ObjectMapper();

        JsonNode jsonNode = null;
        try {
            jsonNode = mapper.readTree(jsonResponse);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        ArrayNode docs = (ArrayNode) getDocs(jsonNode.toString());
        if (docs != null){
            JsonNode doc = docs.get(0);
            if (doc != null) {
                return doc;
            }
        }
     return null;
    }

    public JsonNode getFieldFromDoc(JsonNode doc, String field){
        if (doc != null){
            return doc.get(field);
        }
        return null;
    }

}
