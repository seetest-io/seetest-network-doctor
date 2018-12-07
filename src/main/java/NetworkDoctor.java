import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


import com.experitest.cloud.v2.AccessKeyCloudAuthentication;
import com.experitest.cloud.v2.Cloud;
import com.experitest.cloud.v2.Devices;
import com.experitest.cloud.v2.ProxyInformation;
import com.experitest.cloud.v2.pojo.Device;
import com.experitest.cloud.v2.screen.DeviceConnection;
import org.slf4j.Logger;
import org.slf4j.impl.Log4jLoggerFactory;
import utils.SeeTestProperties;

import static utils.SeeTestProperties.HTTP_PROXY_HOST;

/**
 *
 */
public class NetworkDoctor {

    static Logger LOGGER = new Log4jLoggerFactory().getLogger(NetworkDoctor.class.getName());
    static Properties properties;
    static String googleUrl = "http://google.com";
    static String seetestUrl = "https://cloud.seetest.io";
    static HashMap<String, String> failUrlMap = new HashMap<>();
    static byte[] screen = null;
    static boolean shouldBreak = false;

    private static final String ACCESS_KEY = "";

    public static void main(String s[]) {

        int timeOut = Integer.parseInt(System.getProperty("timeout", "50000"));
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hhmmss");
        System.setProperty("current.date", dateFormat.format(new Date()));
        properties = SeeTestProperties.getSeeTestProperties();

        if (ACCESS_KEY == null || ACCESS_KEY.length() < 10) {
            LOGGER.error("Access key must be set. Please set the access key in the "
                    + NetworkDoctor.class.getSimpleName() + ".java" + " code.");
            LOGGER.info("To get access get to to https://cloud.seetest.io or learn at " +
                    "https://docs.seetest.io/display/SEET/Execute+Tests+on+SeeTest+-+Obtaining+Access+Key", ACCESS_KEY);
            throw new RuntimeException("Access key invalid : accessKey - " + ACCESS_KEY);
        }

        // Proxy
        String httpProxy = properties.getProperty(SeeTestProperties.HTTP_PROXY_HOST);
        String httpProxyPort = properties.getProperty(SeeTestProperties.HTTP_PROXY_HOST, "80");
        if (httpProxy != null && !httpProxy.isEmpty()) {
            System.setProperty("http.proxyHost", httpProxy);
            System.setProperty("http.proxyPort", httpProxyPort == null ? "80" : httpProxyPort);
            LOGGER.info("Using Proxy - " + String.format("%s:%s",httpProxy, httpProxyPort));
        }


        String urls = properties.getProperty(SeeTestProperties.URLS,googleUrl);

        for (String url : urls.split(",")) {
            if (url.equals(seetestUrl)) {
                continue;
            }
            LOGGER.info("Pinging " + url + " ...");
            boolean result = pingURL(url, timeOut);
            LOGGER.info("Ping " + url  + " - " + (result ? "successful" : "failed"));
        }

        LOGGER.info("Pinging " + seetestUrl + " ...");
        boolean result = pingURL(seetestUrl, timeOut);
        LOGGER.info("Ping " + seetestUrl  + " - " + (result ? "successful" : "failed"));

        if (!failUrlMap.isEmpty()) {
            LOGGER.info("Failed Urls and fail reason ...");
            failUrlMap.entrySet().stream().forEach(entry -> LOGGER.info("Url = " + entry.getKey() + " - Reason: " + entry.getValue()));

            if (httpProxy == null || httpProxy.isEmpty()) {
                LOGGER.error(" \n Try connect using proxy and run again." +
                        "Usage: java -Dhttp.proxyHost=<proxyHost> " +
                        "-Dhttp.proxyPort=<proxyPort> NetworkDoctor \n");
            }
        } else {
            LOGGER.info("All the configured urls were connected ...");
        }

        if (!failUrlMap.containsKey(seetestUrl)) {
            cloudConnect(ACCESS_KEY , httpProxy , httpProxyPort);
        }

    }

    /**
     * Pings the URL.
     * @param url
     * @param timeout
     * @return
     */
    public static boolean pingURL(String url, int timeout) {

        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setRequestMethod("HEAD");
            int responseCode = connection.getResponseCode();
            LOGGER.info("Response code - " + responseCode);
            return (200 <= responseCode && responseCode <= 399);

        } catch (UnknownHostException | MalformedURLException | ProtocolException e) {
            //LOGGER.error("Error while connecting. \n" + getTrace(e));
            failUrlMap.put(url, "Exception : " + e.getClass().getSimpleName()
                    + "\n Exception Message : " + e.getLocalizedMessage()
                    + "\n Detail Trace: " + getTrace(e));
            return false;
        }
        catch (IOException e) {
            //LOGGER.error("Error while connecting. \n" + getTrace(exception));
            failUrlMap.put(url, "Exception : " + e.getClass().getSimpleName()
                    + "\n Exception Message : " + e.getLocalizedMessage()
                    + "\n Detail Trace: " + getTrace(e));
            return false;
        }
    }


    /**
     * Connects to the cloud listing devices for the User.
     *
     * Also does WS Connect for configured amount of time.
     *
     * @param accessKey
     * @param proxyHost
     * @param proxyPort
     */
    public static void cloudConnect(String accessKey, String proxyHost , String proxyPort) {
        ProxyInformation proxyInfo =  null;
        if (proxyHost != null && !proxyHost.isEmpty()) {
            proxyInfo = new ProxyInformation(proxyHost, Integer.parseInt(proxyPort.equals("") ? "80" : proxyPort)
                    , null ,null)    ;
        }
        LOGGER.info("Connected to " + SeeTestProperties.SEETEST_IO_APPIUM_URL + "...");
        AccessKeyCloudAuthentication accessKeyAuth = new AccessKeyCloudAuthentication(accessKey);
        Cloud cloud = new Cloud(SeeTestProperties.SEETEST_IO_APPIUM_URL, accessKeyAuth , proxyInfo);

        LOGGER.info("Connected to - " + SeeTestProperties.SEETEST_IO_APPIUM_URL);
        LOGGER.info("Available devices for the User ...");
        LOGGER.info("-----------------------------------");
        cloud.devices()
                .get()
                .stream()
                .filter(d-> d.getStatus().equals(Device.Status.AVAILABLE))
                .forEach(d -> LOGGER.info("" + d.toString()));
        LOGGER.info("-----------------------------------");

        Device firstAvailableDevice = cloud.devices()
                .get()
                .stream()
                .filter(d-> d.getStatus().equals(Device.Status.AVAILABLE)).findFirst().get();

        boolean shouldretry = true;
        DeviceConnection deviceConnection = null;
        int retryCount = 0;
        while (shouldretry) {
            try {
                deviceConnection = cloud.devices().openForAutomation(firstAvailableDevice);
                String deviceDetMsg =
                        String.format("Opening WebSocket Connection and for Automating Device - Name:%s ID:%s ... Success",
                                firstAvailableDevice.getName(),firstAvailableDevice.getId() );
                LOGGER.info(deviceDetMsg);
                break;
            } catch (Exception e) {
                if (retryCount < 4) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    LOGGER.info("Retrying...");
                    retryCount++;
                } else {
                    LOGGER.info("Retry failed.. exiting");
                    shouldretry = false;
                }
            }
        }
        int wsKeepAlivePeriod =
                Integer.parseInt(properties.getProperty(SeeTestProperties.WS_KEEPALIVE_PERIOD, "50000"));
        try {
            deviceConnection.connect((response, screenContent, offset, length) -> screenCheck(screenContent),
                5 * 10000);
            Thread.sleep(wsKeepAlivePeriod);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        LOGGER.info("Attempting to release the device after keeping " +  wsKeepAlivePeriod);
        deviceConnection.close();
        cloud.devices().releaseDevice(firstAvailableDevice.getId());
        cloud.close();
        LOGGER.info("Device released...");
    }
    /**
     * Connecting to cloud and get devices for the user in Json Format.
     * @param url
     * @param accessKey
     * @return

    public static void cloudConnect(String url, String accessKey) {

        BasicHeader[] headers= new BasicHeader[1];
        headers[0] = new BasicHeader("Authorization", "Bearer " + accessKey);
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {


            //htttp request creating
            RequestConfig config = RequestConfig
                    .custom()
                    .setConnectTimeout(3000*1000)
                    .setConnectionRequestTimeout(3000*10000)
                    .setSocketTimeout(4000*10000)
                    .build();
            //producing the real url

            String realUrl =  url + "/api/v1/devices";
            // producing the put request
            HttpGet get = new HttpGet(realUrl);
            get.setConfig(config);
            get.setHeaders(headers);


            HttpResponse responseString = httpClient.execute(get);
            InputStream stream = responseString.getEntity().getContent();
            String result = new BufferedReader(new InputStreamReader(stream))
                    .lines()
                    .collect(Collectors.joining("\n"));

            LOGGER.info("Devices = " + result);

            cloudWSConnect();
        }
        catch (Exception e) {
            // request fail..
            System.out.println();
        }
        //LOGGER.info("Devices = " + devices.toString());
    }*/



    /**
     * Gets the trace.
     * @param
     * @return
     */
    private static String getTrace(Exception e) {
        StackTraceElement[] stack = e.getStackTrace();
        StringBuilder exception = new StringBuilder();
        for (StackTraceElement s : stack) {
            exception.append(s.toString());
            exception.append("\n\t\t");
        }
        return (exception.toString());

    }

    public static boolean checkScreensEq(byte[] sc1, byte[] sc2) {
        //comparing the new screen by bytes, to see the change made..
        for (int i = 96; i < sc1.length; i++) {
            int compare = Byte.compare(sc1[i], sc2[i]);
            if (compare != 0) {
                return false;
            }
        }
        return true;
    }

    private static void screenCheck(byte[] nScreen) {
        LOGGER.info("WebSocket connection is alive...");
        if (screen == null) {
            screen = nScreen;
            //sLOGGER.info("New Screen");
        } else {
            //LOGGER.info("will now start long comparison!");
            boolean checkScreensEq = checkScreensEq(screen, nScreen);
            if (checkScreensEq) {
                //LOGGER.info("screen Didn't change");
            } else {
                //LOGGER.info("Screen changed! will now exit... ");
                shouldBreak = true;
                screen = nScreen;
            }
        }
    }
}
