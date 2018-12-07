# seetest.io - Seetest Network Doctor

This project can be used to check if the Network connectivity to Seetest cloud is successful by pinging many URLS including
seetest cloud with or without proxy.

The test does following.

1. First attempts to connects configured urls. This is to check if some of the urls can be connected or not.
2. It attempts to seetest cloud i.e https://cloud.seetest.io. If this cannot be connected and many of the urls ini (1) can be connected then there is a possibility that seetest url is blocked by firewall.
3. If it is NOT connected, proxy can be set and an attempt can be done again to check if connectivity is there via proxy. If still not able to connect then Contact Network Administrator.
4. If connection is successful then the utility will attempt to get all devices using the Access Key.
5. If this is successful, utility will attempt and Web Socket Connection to the cloud to check the longevity of the connection.


### Steps to run demo test

1. Clone this git repository

	```
	git clone
	```

2. Obtain access key from seetest.io cloud

    https://docs.seetest.io/display/SEET/Execute+Tests+on+SeeTest+-+Obtaining+Access+Key

    note :  you need to have a valid subscription or a trial to run this test (Trial \ paid)

3. To run the tests,
    Please ensure that following environment variables are set.

    1. JAVA_HOME to JDK/JRE HOME and update it in the PATH variable.
    2. Set the variable ACCESS_KEY in the code with your access key (got from  step 2)

    Run the test using command below.

    ```
    gradlew runNetworkDoctor
    ```

    Note: If you want to run the utility using proxy configure proxy host and proxy port settings in \main\resources\seetest.properties

        http.proxyHost=
        http.proxyPort=

    Note: If you want to add new Urls configure 'urls' property in \main\resources\seetest.properties.

        urls=http://google.com,https://edition.cnn.com/









