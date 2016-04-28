package uk.ac.ebi.fg.biosd.annotator.olsclient.ontodiscovery;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.fg.biosd.annotator.olsclient.client.OLSClient;
import uk.ac.ebi.fg.biosd.annotator.olsclient.model.ClassRef;
import uk.ac.ebi.fg.biosd.annotator.olsclient.model.TextAnnotation;
import uk.ac.ebi.onto_discovery.api.OntologyDiscoveryException;
import uk.ac.ebi.onto_discovery.api.OntologyTermDiscoverer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static uk.ac.ebi.onto_discovery.api.CachedOntoTermDiscoverer.NULL_RESULT;

/**
 * Created by olgavrou on 25/04/2016.
 */
public class OLSOntoTermDiscoverer extends OntologyTermDiscoverer {

    private OLSClient olsClient;


    private Logger log = LoggerFactory.getLogger ( this.getClass () );


    public OLSOntoTermDiscoverer() {
        this (new OLSClient());
    }

    public OLSOntoTermDiscoverer(OLSClient olsClient) {
        this.olsClient = olsClient;
    }

    @Override
    public List<DiscoveredTerm> getOntologyTerms(String valueLabel, String typeLabel) throws OntologyDiscoveryException {
        if ( (valueLabel = StringUtils.trimToNull ( valueLabel )) == null ) return NULL_RESULT;

        List<DiscoveredTerm> result = getOntologyTermsFromOLS ( valueLabel );
        // If you fail with the value, try the type instead
        if ( result != NULL_RESULT || typeLabel == null ) return result;

        return getOntologyTermsFromOLS ( typeLabel );
    }

    public List<DiscoveredTerm> getOntologyTermsFromOLS(String text) throws OntologyDiscoveryException {
        List<DiscoveredTerm> result = new ArrayList<>();

        try
        {
            TextAnnotation textAnnotation;

            textAnnotation = olsClient.getTextAnnotations ( text );

            // Collect the results
            // we only return one result from OLS but we give it back as a List since that is the way it is implemented

            if ( textAnnotation == null ) return NULL_RESULT;

            Set<String> visitedIris = new HashSet<>();

            ClassRef classRef = textAnnotation.getAnnotatedClass ();
            if ( classRef == null ) return NULL_RESULT;
            String classIri = classRef.getClassIri ();
            visitedIris.add ( classIri );

            String classLabel = null;


       /*     OntologyClass ontoClass = olsClient.getOntologyClass ( classRef.getOntologyAcronym (), classIri );
            if ( ontoClass != null ) {
                classLabel = ontoClass.getPreferredLabel();
            }*/


            result.add ( new DiscoveredTerm ( classIri, (Double) null, null, "OLS Annotator" ) );


            if ( result.size () == 0 ) return NULL_RESULT;
            return result;
        }
        catch ( Exception ex )
        {
            log.error ( String.format (
                    "Error while invoking OLS for '%s': %s. Returning null", text, ex.getMessage ()
            ));
            if ( log.isDebugEnabled () ) log.debug ( "Underline exception is:", ex );
        }
        return result;
    }

    public OLSClient getOlsClient() {
        return olsClient;
    }

    public void setOlsClient(OLSClient olsClient) {
        this.olsClient = olsClient;
    }
}
