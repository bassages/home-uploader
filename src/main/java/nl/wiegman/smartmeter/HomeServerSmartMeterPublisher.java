package nl.wiegman.smartmeter;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class HomeServerSmartMeterPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(HomeServerSmartMeterPublisher.class);

    @Value("${home-server-rest-service-url:#{null}}")
    private String homeServerRestServiceUrl;

    @Value("${home-server-rest-service-basic-auth-user:#{null}}")
    private String homeServerRestServiceBasicAuthUser;
    @Value("${home-server-rest-service-basic-auth-password:#{null}}")
    private String homeServerRestServiceBasicAuthPassword;

    public void publish(SmartMeterMessage smartMeterMessage) {

        try {
            String url = String.format(homeServerRestServiceUrl + "/meterstanden");
            String jsonMessage = createSmartMeterJsonMessage(smartMeterMessage);

            try {
                postJson(url, jsonMessage);
            } catch (Exception e) {
                LOG.warn("Post to url [" + url + "] failed.", e);
            }

        } catch (JsonProcessingException e) {
            LOG.error("Failed to map message to json. Message=" + smartMeterMessage, e);
        }
    }

    private void postJson(String url, String jsonString) throws Exception {
        LOG.debug("Post to url: {}. Request body: {}", url, jsonString);

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {

            HttpPost request = new HttpPost(url);
            StringEntity params = new StringEntity(jsonString, ContentType.APPLICATION_JSON);
            request.setEntity(params);

            setAuthorizationHeader(request);

            CloseableHttpResponse response = httpClient.execute(request);

            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_CREATED) {
                throw new RuntimeException("Unexpected statusline: " + response.getStatusLine());
            }
        }
    }

    private void setAuthorizationHeader(HttpPost request) {
        if (homeServerRestServiceBasicAuthUser != null
                && homeServerRestServiceBasicAuthPassword != null) {
            String auth = homeServerRestServiceBasicAuthUser + ":" + homeServerRestServiceBasicAuthPassword;
            byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.UTF_8));
            String authorizationHeader = "Basic " + new String(encodedAuth);
            request.setHeader(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
    }

    private String createSmartMeterJsonMessage(SmartMeterMessage smartMeterMessage) throws JsonProcessingException {
        HomeServerMeterstand homeServerMeterstand = mapToHomeServerMeterstand(smartMeterMessage);
        return new ObjectMapper().writeValueAsString(homeServerMeterstand);
    }

    private HomeServerMeterstand mapToHomeServerMeterstand(SmartMeterMessage smartMeterMessage) {
        HomeServerMeterstand homeServerMeterstand = new HomeServerMeterstand();
        homeServerMeterstand.setDatumtijd(smartMeterMessage.getDatetimestamp().getTime());
        homeServerMeterstand.setStroomOpgenomenVermogenInWatt(smartMeterMessage.getActualElectricityPowerDelivered().multiply(new BigDecimal(1000.0d)).intValue());
        homeServerMeterstand.setStroomTarief1(smartMeterMessage.getMeterReadingElectricityDeliveredToClientTariff1());
        homeServerMeterstand.setStroomTarief2(smartMeterMessage.getMeterReadingElectricityDeliveredToClientTariff2());
        homeServerMeterstand.setGas(smartMeterMessage.getLastHourlyValueGasDeliveredToClient());
        return homeServerMeterstand;
    }

    private static class HomeServerMeterstand {
        private long datumtijd;
        private int stroomOpgenomenVermogenInWatt;
        private BigDecimal stroomTarief1;
        private BigDecimal stroomTarief2;
        private BigDecimal gas;

        public long getDatumtijd() {
            return datumtijd;
        }

        public void setDatumtijd(long datumtijd) {
            this.datumtijd = datumtijd;
        }

        public int getStroomOpgenomenVermogenInWatt() {
            return stroomOpgenomenVermogenInWatt;
        }

        public void setStroomOpgenomenVermogenInWatt(int stroomOpgenomenVermogenInWatt) {
            this.stroomOpgenomenVermogenInWatt = stroomOpgenomenVermogenInWatt;
        }

        public BigDecimal getStroomTarief1() {
            return stroomTarief1;
        }

        public void setStroomTarief1(BigDecimal stroomTarief1) {
            this.stroomTarief1 = stroomTarief1;
        }

        public BigDecimal getStroomTarief2() {
            return stroomTarief2;
        }

        public void setStroomTarief2(BigDecimal stroomTarief2) {
            this.stroomTarief2 = stroomTarief2;
        }

        public BigDecimal getGas() {
            return gas;
        }

        public void setGas(BigDecimal gas) {
            this.gas = gas;
        }
    }
}
