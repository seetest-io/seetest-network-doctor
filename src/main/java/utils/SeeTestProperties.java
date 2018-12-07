package utils;

import org.slf4j.Logger;
import org.slf4j.impl.Log4jLoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import java.util.Properties;

public class SeeTestProperties {

    public static final String PROPERTIES_FILENAME = "seetest.properties";
    public static String pathToProperties;
    static Logger LOGGER = new Log4jLoggerFactory().getLogger("SeeTestProperties");
    public static final String URLS = "urls";
    public static final String HTTP_PROXY_HOST = "http.ProxyHost";
    public static final String HTTP_PROXY_PORT = "http.proxyPort";
    public static String SEETEST_IO_APPIUM_URL = "https://cloud.seetest.io";
    public static String WS_KEEPALIVE_PERIOD = "ws.keepalive.period";

    /**
     * Loads properties.
     */
    public static Properties getSeeTestProperties() {
        Properties properties = new Properties();

        LOGGER.info("Enter loadInitProperties() ...");
        pathToProperties = Objects.requireNonNull(SeeTestProperties.class.getClassLoader().getResource(PROPERTIES_FILENAME)).getFile();

        try (FileReader fr = new FileReader(pathToProperties)) {
            properties.load(fr);
        } catch (IOException e) {
            LOGGER.warn("Could not load init properties", pathToProperties, e);
            throw new RuntimeException("Could not init properties from path!", e);
        }
        LOGGER.info("Loading properties from .. " + pathToProperties);
        LOGGER.info("--------- List Of Properties ---------");
        properties.forEach((key, value) -> LOGGER.info("Key = " + key + " ; Value = " + value));
        LOGGER.info("--------- END ---------");

        LOGGER.info("Exit loadInitProperties() ...");
        return properties;
    }
}
