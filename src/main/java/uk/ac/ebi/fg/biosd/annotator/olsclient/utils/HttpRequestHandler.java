package uk.ac.ebi.fg.biosd.annotator.olsclient.utils;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * Created by olgavrou on 02/12/2015.
 */
public class HttpRequestHandler {


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

    public String executeHttpDelete(String path, Map<String,String> params) throws IOException, URISyntaxException {
        HttpDelete httpDelete;
        URI uri = null;
        if(params == null){
            httpDelete = new HttpDelete(path);
        } else {
            URIBuilder uriBuilder = new URIBuilder()
                    .setPath(path);
            if (params != null) {
                for (String key : params.keySet()) {
                    uriBuilder.addParameter(key, params.get(key));
                }
            }
            uri = uriBuilder.build();
            httpDelete = new HttpDelete(uri);

        }

        httpDelete.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        HttpClient client = new DefaultHttpClient();

        HttpResponse response = client.execute(httpDelete);
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Failed : HTTP error code : " + response.getStatusLine().getStatusCode() + " on DELETE: " + uri);
        }

        return getStringFromInputStream(response.getEntity().getContent());

    }

    public String executeHttpGet(String path, Map<String,String> params) throws IOException, URISyntaxException {
        //gets a set of parameters, builds uri and executes a GET method
        //returns the content of the GET method]
        HttpGet httpGet;
        URI uri = null;
        if(params == null){
            httpGet = new HttpGet(path);
        } else {
            URIBuilder uriBuilder = new URIBuilder()
                    .setPath(path);
            for (String key : params.keySet()) {
                uriBuilder.addParameter(key, params.get(key));
            }

            uri = uriBuilder.build();
            httpGet = new HttpGet(uri);

        }

        httpGet.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        HttpClient client = HttpClientBuilder.create().build();

        HttpResponse response = client.execute(httpGet);
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Failed : HTTP error code : " + response.getStatusLine().getStatusCode() + " on GET: " + uri);
        }

        return getStringFromInputStream(response.getEntity().getContent());

    }

    //Post with JSON
    public String executeHttpPost(String path, String json) throws IOException, URISyntaxException {
        //gets a set of parameters, builds uri and executes a POST method
        //returns the content of the POST method
        URI uri = new URIBuilder()
                .setPath(path)
                .build(); //http://localhost:8081/archive-web-services/archive/templates
        HttpPost httpPost = new HttpPost(uri);

        httpPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");

        httpPost.setEntity(new StringEntity(json));
        HttpClient client = HttpClientBuilder.create().build();
        HttpResponse response = client.execute(httpPost);
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Failed : HTTP error code : " + response.getStatusLine().getStatusCode() + " on POST: " + uri);
        }

        return getStringFromInputStream(response.getEntity().getContent());

    }

    //Post with passing parameters
    public String executeHttpPost(String path, Map<String,String> params) throws IOException, URISyntaxException {
        //gets a set of parameters, builds uri and executes a POST method
        //returns the content of the POST method]
        HttpPost httpPost;
        URI uri = null;
        if(params == null){
            httpPost = new HttpPost(path);
        } else {
            URIBuilder uriBuilder = new URIBuilder()
                    .setPath(path); //http://localhost:8081/archive-web-services/archive/templates
            for (String key : params.keySet()) {
                uriBuilder.addParameter(key, params.get(key));
            }

            uri = uriBuilder.build();
            httpPost = new HttpPost(uri);

        }

        httpPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        HttpClient client = HttpClientBuilder.create().build();

        HttpResponse response = client.execute(httpPost);
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Failed : HTTP error code : " + response.getStatusLine().getStatusCode() + " on POST: " + uri);
        }

        return getStringFromInputStream(response.getEntity().getContent());

    }


}
