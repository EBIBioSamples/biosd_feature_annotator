package uk.ac.ebi.fg.biosd.annotator.olsclient.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import uk.ac.ebi.fg.biosd.annotator.olsclient.model.ClassRef;
import uk.ac.ebi.fg.biosd.annotator.olsclient.model.OntologyClass;
import uk.ac.ebi.fg.biosd.annotator.olsclient.model.TextAnnotation;
import uk.ac.ebi.fg.biosd.annotator.olsclient.utils.OLSResponseParser;
import uk.ac.ebi.fg.biosd.annotator.olsclient.utils.OLSWebServiceUtils;

/**
 * Created by olgavrou on 19/04/2016.
 */
public class OLSClient {
    String olsLocation = "http://www.ebi.ac.uk/ols/beta";

    public OLSClient ()  { this ( (String) null ); }

    public OLSClient ( URL olsLocation ) { this ( olsLocation == null ? null : olsLocation.toString () ); }

    public OLSClient(String olsLocation) {
        if (olsLocation != null) {
            this.olsLocation = olsLocation;
        }
    }

    /*
    Searches the Ontology Lookup Service for a correct term iri, given an ontology acronym and an accession
    @ontologyAcronym: the proposed ontology acronym
    @accession: the proposed accession
    returns: an OntologyClass that holds the correct term iri, its preferred label and its ontology prefix
     */
    public OntologyClass getOntologyClass (String ontologyAcronym, String accession ) {

        if(accession == null){
            return null;
        }

        //find correct ontology from ontologyAcronym
        OLSWebServiceUtils olsWebServiceUtils = new OLSWebServiceUtils();
        String correctOntAcronym = olsWebServiceUtils.getOntology(olsLocation, ontologyAcronym);

        List<String> tryAccessions = new ArrayList<>();
        tryAccessions.add(getCorrectAccession(correctOntAcronym, accession, true)); //first try the clean accession (not https) and with the ontology prefix added if the accession was just a number
        tryAccessions.add(accession); //then try the accession as it is given from the user
        tryAccessions.add(getCorrectAccession(correctOntAcronym, accession, false)); //then add the clean accession without the ontology prefix


        for (String acc : tryAccessions){
            String response = olsWebServiceUtils.getOntologyClass(olsLocation, correctOntAcronym, acc, true);
            if (response != null){
                OntologyClass ontologyClass = buildOntologyClass(response);
                if (ontologyClass != null){
                    return ontologyClass;
                }
            }
        }
        return null;
    }

    public TextAnnotation getTextAnnotations ( String textValue){

        OLSWebServiceUtils olsWebServiceUtils = new OLSWebServiceUtils();

        ArrayList<String> responses = new ArrayList<>();
        responses.add(olsWebServiceUtils.getOntologyClass(olsLocation, "efo", textValue, true)); //first search in efo for exact match
        responses.add(olsWebServiceUtils.getOntologyClass(olsLocation, null, textValue, true)); //then search in all ontologies for exact match
        responses.add(olsWebServiceUtils.getOntologyClass(olsLocation, "efo", textValue, false)); // search in efo for loose match
        responses.add(olsWebServiceUtils.getOntologyClass(olsLocation, null, textValue, false)); // search in all ontologies for loose match


        OntologyClass ontologyClass = null;

        for (String response : responses) {
            if (response != null) {
                ontologyClass = buildOntologyClass(response);
                if (ontologyClass != null) {
                    return new TextAnnotation(new ClassRef(ontologyClass.getIri(), ontologyClass.getOntologyAcronym()));
                }
            }
        }
        return null;
    }

    /*
    Given an accession it will remove the uri prefixes and return only the proposed term
    If the accession is only a number, it will prefix the ontology prefix if provided (e.g. 3750 -> NCBITaxon:3750)
     */
    public String getCorrectAccession(String ontologyPrefix, String accession, boolean addPrefix){
        String correctAcc = accession;
        Boolean isNumber = true;

        //just get the accession without the uri stuff
        if ((accession.contains("http") || accession.contains("https") || accession.contains("www")) && accession.contains("/")) {
            String[] acc = accession.split("/");
            correctAcc = acc[acc.length - 1]; //the last part of a url type: http://www.ebi.ac.uk/efo/EFO_0000270
        }

        //if the user has given only a number, put the ontology acronym in front, if there is an ontology acronym
        //TODO: think if there is no ontology prefix and only an accession number, do we want to get a mapping from ols back?
        if (ontologyPrefix != null && addPrefix) {
            try {
                Integer.parseInt(correctAcc);
            } catch (NumberFormatException e) {
                isNumber = false;
            }
            if (isNumber) {
                correctAcc = ontologyPrefix + ":" + correctAcc;
            }
        }

        return correctAcc;
    }

    /*
    Gets the jsonResponse of the OLS query and
    returns an OntologyClass built from what was found in OLS
     */
    public OntologyClass buildOntologyClass (String jsonResponse ) {
        OntologyClass result = new OntologyClass();

        JsonNode iri = null;
        JsonNode label = null;
        JsonNode ontologyPrefix = null;

        OLSResponseParser olsParser = new OLSResponseParser();
        JsonNode doc = null;
        doc = olsParser.getFirstDoc(jsonResponse);
        iri = olsParser.getFieldFromDoc(doc, "iri");
        label = olsParser.getFieldFromDoc(doc, "label");
        ontologyPrefix = olsParser.getFieldFromDoc(doc, "ontology_prefix");

        if (iri != null) {
            result.setIri(iri.asText());
        } else {
            return null; // if we couldn't get the iri, there probably was no annotation found
        }
        if (label != null) {
            result.setPreferredLabel(label.asText());
        }
        if (ontologyPrefix != null){
            result.setOntologyAcronym(ontologyPrefix.asText());
        }

        return result;

    }

    public static void main(String[] args){
        OLSClient olsClient = new OLSClient();

        olsClient.getOntologyClass("NCBI Taxonomy","3750");
        olsClient.getOntologyClass("EFO","EFO_0000182");
        olsClient.getOntologyClass("EFO","EFO_0000993");

    }

}

