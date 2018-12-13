import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;


import com.experitest.cloud.v2.AccessKeyCloudAuthentication;
import com.experitest.cloud.v2.Cloud;
import com.experitest.cloud.v2.ProxyInformation;
import com.experitest.cloud.v2.pojo.Device;
import com.experitest.cloud.v2.screen.DeviceConnection;
import org.slf4j.Logger;
import org.slf4j.impl.Log4jLoggerFactory;
import utils.SeeTestProperties;


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
        
        //Verifying access key is provides. Valid user access key is needed to complete the test.
        if (ACCESS_KEY == null || ACCESS_KEY.length() < 10) {
            LOGGER.error("Access key must be set. Please set the access key in the "
                    + NetworkDoctor.class.getSimpleName() + ".java" + " code.");
            LOGGER.info("To get access get to to https://cloud.seetest.io or learn at " +
                    "https://docs.seetest.io/display/SEET/Execute+Tests+on+SeeTest+-+Obtaining+Access+Key", ACCESS_KEY);
            throw new RuntimeException("Access key invalid : accessKey - " + ACCESS_KEY);
        }

        // Proxy settings
        String httpProxy = properties.getProperty(SeeTestProperties.HTTP_PROXY_HOST);
        String httpProxyPort = properties.getProperty(SeeTestProperties.HTTP_PROXY_HOST, "80");
        if (httpProxy != null && !httpProxy.isEmpty()) {
            System.setProperty("http.proxyHost", httpProxy);
            System.setProperty("http.proxyPort", httpProxyPort == null ? "80" : httpProxyPort);
            LOGGER.info("Using Proxy - " + String.format("%s:%s",httpProxy, httpProxyPort));
        }

        ////////////////////////////////////////////////////////////////////////////////////////
        //
        // Verifying we have access to common sites
        //
        ////////////////////////////////////////////////////////////////////////////////////////
        String urls = properties.getProperty(SeeTestProperties.URLS,googleUrl);
        for (String url : urls.split(",")) {
            if (url.equals(seetestUrl)) {
                continue;
            }
            LOGGER.info("Pinging " + url + " ...");
            boolean result = pingURL(url, timeOut);
            LOGGER.info("Ping " + url  + " - " + (result ? "successful" : "failed"));
        }
        
        ////////////////////////////////////////////////////////////////////////////////////////
        //
        // Verifying we have access to cloud.seetest.io
        //
        ////////////////////////////////////////////////////////////////////////////////////////
        LOGGER.info("Pinging " + seetestUrl + " ...");
        boolean result = pingURL(seetestUrl, timeOut);
        LOGGER.info("----------------------  Ping " + seetestUrl  + " - " + (result ? "successful" : "failed") + "------------------------");

        ////////////////////////////////////////////////////////////////////////////////////////
        //
        // Verifying we have access to all seetest.io data centers
        //
        ////////////////////////////////////////////////////////////////////////////////////////
        HashMap<String, String> datacentersMap = new HashMap<>();
        datacentersMap.put("eu-1 CloudIO-DHM1 5003d9", "http://62.90.196.20:8080");
        datacentersMap.put("eu-1 CloudIO-DHM3 e6facf", "http://62.90.196.21:8080");
        datacentersMap.put("eu-1 CloudIO-DHM2 782cbf", "http://62.90.196.11:8080");
        datacentersMap.put("au-1 au-dhm1 fa6eea", "http://125.7.125.30:8080");
        datacentersMap.put("gr-1 gr-dhm1 40bf8f", "http://137.221.39.141:8080");
        datacentersMap.put("eu-1 CloudIO-DHM6 af65d2", "http://62.90.196.29:8080");
        datacentersMap.put("eu-1 CloudIO-DHM4 135947", "http://62.90.196.19:8080");
        datacentersMap.put("uk-1 uk-dhm1 ecd166", "http://37.203.43.146:8080");
        datacentersMap.put("eu-1 CloudIO-DHM5 568dae", "http://62.90.196.14:8080");
        datacentersMap.put("us-1 us1-dhm1 9f76dd", "http://65.49.79.140:8080");
        datacentersMap.put("eu-1 CloudIO-DHM7 50c4fd", "http://62.90.196.30:8080");
        datacentersMap.put("ca-1 ca1-dhm1 ad0682", "http://206.198.185.120:8080");
        datacentersMap.put("us-1 us1-dhm2 f0ace5", "http://65.49.79.141:8080");
        
        LOGGER.info("-----------------------------------------  Starting to check datacenters --------------------------------------------------------");
        for (String name : datacentersMap.keySet()) {
        	String url = datacentersMap.get(name);
            result = pingURL(url, timeOut);
            LOGGER.info(String.format("Ping %s datacenter, address is %s , result is %s", name,url,(result ? "successful" : "failed")));        	

        }        
        ////////////////////////////////////////////////////////////////////////////////////////
        //
        // Phase 1 results
        //
        ////////////////////////////////////////////////////////////////////////////////////////        
        if (!failUrlMap.isEmpty()) {
        	LOGGER.info("------------------------------------------- Failure --------------------------------------------------------");
            LOGGER.info("Failed Urls and fail reason ...");
            failUrlMap.entrySet().stream().forEach(entry -> LOGGER.info("Url = " + entry.getKey() + " - Reason: " + entry.getValue()));
            if (httpProxy == null || httpProxy.isEmpty()) {
                LOGGER.error(" \n Try connect using proxy and run again." +
                        "Usage: java -Dhttp.proxyHost=<proxyHost> " +
                        "-Dhttp.proxyPort=<proxyPort> NetworkDoctor \n");
            }
            LOGGER.info("------------------------------------------------------------------------------------------------------------");
            System.exit(1);
        } else {
            LOGGER.info("------------------------------------------ All the configured urls were connected ... -------------------------");
        }

        ////////////////////////////////////////////////////////////////////////////////////////
        //
        // Verifying account to seetest api, and websocket
        //
        ////////////////////////////////////////////////////////////////////////////////////////        
        cloudConnect(ACCESS_KEY , httpProxy , httpProxyPort);
        
        LOGGER.info("------------------------------------------  Network Doctor Completed  -------------------------");
    }

   
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
//   UTILITY METHODS
//
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Pings the URL.
     * @return true/false
     */
    private static boolean pingURL(String url, int timeout) {

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
    private static void cloudConnect(String accessKey, String proxyHost , String proxyPort) {
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

    private static boolean checkScreensEq(byte[] sc1, byte[] sc2) {
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
