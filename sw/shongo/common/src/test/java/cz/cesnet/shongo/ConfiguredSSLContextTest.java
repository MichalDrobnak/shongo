package cz.cesnet.shongo;

import cz.cesnet.shongo.ssl.ConfiguredSSLContext;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Tests for {@link ConfiguredSSLContext}.
 *
 * @author Martin Srom <martin.srom@cesnet.cz>
 */
public class ConfiguredSSLContextTest
{
    @Test
    public void test() throws Exception
    {
        ConfiguredSSLContext sslContext = ConfiguredSSLContext.getInstance();
        sslContext.addAdditionalCertificates("actest-w3.cesnet.cz");
        sslContext.addTrustedHostMapping("shongo-auth-dev.cesnet.cz", "hroch.cesnet.cz");

        System.out.println(requestByConnection("https://actest-w3.cesnet.cz/api/xml"));
        System.out.println(requestByHttpClient("https://actest-w3.cesnet.cz/api/xml"));
        System.out.println(requestByConnection("https://shongo-auth-dev.cesnet.cz/phpid-server/oic/authorize"));
        System.out.println(requestByHttpClient("https://shongo-auth-dev.cesnet.cz/phpid-server/oic/authorize"));
    }

    private String requestByConnection(String url) throws Exception
    {
        URL loginUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) loginUrl.openConnection();
        connection.connect();

        InputStream inputStream;
        if (connection.getResponseCode() == 200) {
            inputStream = connection.getInputStream();
        }
        else {
            inputStream = connection.getErrorStream();
        }
        StringBuilder result = new StringBuilder();
        BufferedReader bufferReader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while ((line = bufferReader.readLine()) != null) {
            result.append(line);
        }
        inputStream.close();
        if (connection.getResponseCode() != 200) {
            result.insert(0, "ERROR " + connection.getResponseCode() + ": ");
        }
        return result.toString();
    }

    private String requestByHttpClient(String url) throws Exception
    {
        HttpClient httpClient = ConfiguredSSLContext.getInstance().createHttpClient();
        HttpResponse response = httpClient.execute(new HttpGet(url));
        HttpEntity entity = response.getEntity();
        StringBuilder result = new StringBuilder();
        if (entity != null) {
            InputStream inputStream = entity.getContent();
            BufferedReader bufferReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = bufferReader.readLine()) != null) {
                result.append(line);
            }
            inputStream.close();
        }
        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            result.insert(0, "ERROR " + response.getStatusLine().getStatusCode() + ": ");
        }
        return result.toString();
    }
}
