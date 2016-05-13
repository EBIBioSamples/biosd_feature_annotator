package uk.ac.ebi.fg.biosd.annotator.olsclient.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.utils.URIBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

/**
 * Created by olgavrou on 02/12/2015.
 */
public class HttpRequestHandler {

    public String executeHttpGet(String path, Map<String,String> params) throws IOException, URISyntaxException {
        //gets a set of parameters, builds uri and executes a GET method
        //returns the content of the GET method]
        URIBuilder uriBuilder;
        URL url = null;

        if(params == null){
            uriBuilder = new URIBuilder()
                    .setPath(path);
        } else {
            uriBuilder = new URIBuilder()
                    .setPath(path);
            for (String key : params.keySet()) {
                uriBuilder.addParameter(key, params.get(key));
            }
        }

        url = uriBuilder.build().toURL();

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod ( "GET" );
        conn.setRequestProperty ( "Accept", "application/json" );
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + responseCode + " on GET: " + url);
        }
        return getStringFromInputStream ( conn.getInputStream () );

    }


    // utility function to convert InputStream to String
    private static String getStringFromInputStream(InputStream is) {

        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();

        String line;
        InputStreamReader inputStreamReader = new InputStreamReader(is);
        try {

            br = new BufferedReader(inputStreamReader);
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (inputStreamReader != null){
                try {
                    inputStreamReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (is != null){
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return sb.toString();
    }

}
