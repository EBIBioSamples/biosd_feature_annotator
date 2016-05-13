package uk.ac.ebi.fg.biosd.annotator.olsclient.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.bioportal.webservice.model.Ontology;
import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.utils.runcontrol.ChainExecutor;
import uk.ac.ebi.utils.runcontrol.DynamicRateExecutor;
import uk.ac.ebi.utils.runcontrol.RateLimitedExecutor;
import uk.ac.ebi.utils.runcontrol.StatsExecutor;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by olgavrou on 20/04/2016.
 */
public class OLSWebServiceUtils {


    private static Logger log = LoggerFactory.getLogger ( OLSWebServiceUtils.class );

    /*
    From an ontologyAcronym it searches OLS for a correct ontology prefix
    Searches first for an exact match, and if none found searches without exact match
    e.g. NCBI Taxonomy will return NCBITaxon
    @olsLocation: the ols uri
    @ontoAcronym: the proposed ontology prefix
    returns: the correct ontology prefix, if any
     */
    public String getOntology(String olsLocation, String ontoAcronym){

        // Do you have it in memory?
        Table<Class, String, Object> store = AnnotatorResources.getInstance ().getStore ();
        String acronym = (String) store.get(Ontology.class, ontoAcronym);
        if (acronym != null) return acronym;

        Map<String, String> params = new HashMap<>();
        params.put("type", "ontology");
        String response = invokeOLS(olsLocation, ontoAcronym, params, true); // first search for exact match

        OLSResponseParser olsParser = new OLSResponseParser();
        JsonNode doc = olsParser.getFirstDoc(response);
        if (doc == null){ // not found, now search withought exact
            response = invokeOLS(olsLocation, ontoAcronym, params, false); // first search for exact match
            doc = olsParser.getFirstDoc(response);
        }

        JsonNode ontologyPrefix =  olsParser.getFieldFromDoc(doc, "ontology_prefix");
        if (ontologyPrefix != null){
            if ( !store.contains ( Ontology.class, acronym ) )
                store.put ( Ontology.class, ontoAcronym, ontologyPrefix.asText() );
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

        response = invokeOLS(olsLocation, q, parameters, exact);

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
    public static String invokeOLS(final String olsLocation, final String q, final Map map, final boolean exact){

        final String[] response = {null};

        wrapExecutor.execute ( new Runnable() {
            @Override
            public void run () {
                Map<String, String> params = new HashMap<>();
                params.put("q", q);
                params.put("exact", String.valueOf(exact));
                params.putAll(map);
                HttpRequestHandler requestHandler = new HttpRequestHandler();

                try {
                    response[0] = requestHandler.executeHttpGet(olsLocation + "/api/search", params);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            }
        });

        return response[0];

    }

    //======================================================================//

    public static final StatsExecutor STATS_WRAPPER = new StatsExecutor ("OLS"); //, Long.parseLong ( System.getProperty ( STATS_SAMPLING_TIME_PROP_NAME, "" + 5 * 60 * 1000 )
    public static final DynamicRateExecutor RATE_LIMITING_WRAPPER = new OLSRateLimiter ();

    /**
     * This is used in {@link #invokeOLS(String, String, Map, boolean)}, We have wrap that call with
     * both a {@link RateLimitedExecutor rate limit wrapper} and a {@link StatsExecutor statistical reporter}.
     * The former is needed because Bioportal's server doesn't like to be hammered at speeds higher than
     * 15 calls/sec per process.
     */
    private static ChainExecutor wrapExecutor = new ChainExecutor (
            RATE_LIMITING_WRAPPER,
            STATS_WRAPPER
    );

    /**
     * Our own version, that adapts dynamically to the current performance, using
     * {@link OLSWebServiceUtils#STATS_WRAPPER}.
     */
    protected static class OLSRateLimiter extends DynamicRateExecutor
    {
        public final double maxRate;

        public OLSRateLimiter () {
            this ( Double.MAX_VALUE );
        }

        public OLSRateLimiter ( double maxRate )
        {
            super ( maxRate );
            this.maxRate = maxRate;
        }


        @Override
        protected synchronized double setNewRate ()
        {
            int totCalls = STATS_WRAPPER.getLastTotalCalls ();
            if ( totCalls == 0 ) return maxRate;

            double failedCalls = STATS_WRAPPER.getLastFailedCalls () / (double) totCalls;
            if ( failedCalls <= 0.1 )
            {
                if ( Math.abs ( this.getRate () / this.maxRate - 1 ) > 1d/1000 )
                    // was throttling, going back to normal
                    log.info ( "OLS back to good performance, throttling ends" );
                return maxRate;
            }

            // Degrade the performance gently
            //
            double rate =
                    failedCalls <= 0.30 ? maxRate * 0.8
                            : failedCalls <= 0.50 ? maxRate * 0.5
                            : failedCalls <= 0.70 ? maxRate * 0.2
                            : 0.5;

            if ( Math.abs ( this.getRate () / rate - 1 ) > 1d/1000 )
                // Wasn't throttling, starting now
                log.info ( "Throttling OLS to avoid too many fails, calls are slowed down to {} calls/s", rate );

            return rate;
        } // setNewRate
    }	// OLSRateLimiter

}
