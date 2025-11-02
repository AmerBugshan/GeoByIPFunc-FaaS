package sa.edu.kau.fcit.cpit490;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.util.Optional;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

/**
 * Azure Functions with HTTP Trigger.
 */
public class Function {

    /**
     * This function listens at endpoint "/api/getLocation". To invoke it using
     * "curl" command in bash:
     * curl "{your host}/api/getLocation?ip=IPv4_ADDRESS"
     */
    @FunctionName("getLocation")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {
                    HttpMethod.GET }, authLevel = AuthorizationLevel.FUNCTION) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        // Get the IPv4 address as a query parameter
        final String ipAddress = request.getQueryParameters().get("ip");

        if (ipAddress == null) {
            context.getLogger().warning("Java HTTP trigger processed a request.");
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Please provide an IP address using the 'ip' query parameter")
                    .build();
        } else {
            try {
                String countryCode = getCountryFromIP(ipAddress, context);
                if (countryCode != null) {
                    Map<String, String> responseData = new HashMap<>();
                    responseData.put("ip", ipAddress);
                    responseData.put("countryCode", countryCode);
                    return request.createResponseBuilder(HttpStatus.OK).body(responseData).build();
                } else {
                    return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                            .body("Country not found for IP: " + ipAddress)
                            .build();
                }
            } catch (Exception e) {
                context.getLogger().severe("Failed to find country for IP: " + e.getMessage());
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to find country for IP:  " + e.getMessage())
                        .build();
            }
        }
    }

    /**
     * Gets country code for an IPv4 address
     * 
     * @param ipAddress The IPv4 address to look up
     * @return The two-letter country code or null if not found
     */
    public String getCountryFromIP(String ipAddress, ExecutionContext context) {
        try {
            long ipLong = ipToLong(ipAddress);
            return lookupCountryCode(ipLong, context);
        } catch (Exception e) {
            context.getLogger().warning("Error looking up IP: " + e.getMessage());
            return null;
        }
    }

    /**
     * Converts an IPv4 address to a long integer for range comparison
     * 
     * @param ipAddress The IPv4 address in string format (e.g., "192.168.1.1")
     * @return The IP address as a long integer
     * @throws UnknownHostException if the IP address is invalid
     */
    public long ipToLong(String ipAddress) throws UnknownHostException {
        String[] parts = ipAddress.split("\\.");
        if (parts.length != 4) {
            throw new UnknownHostException("Invalid IPv4 format: " + ipAddress);
        }

        InetAddress inet = InetAddress.getByName(ipAddress);
        byte[] addressBytes = inet.getAddress();

        if (addressBytes.length != 4) {
            throw new UnknownHostException("Not an IPv4 address");
        }

        long result = 0;
        for (byte b : addressBytes) {
            result = (result << 8) | (b & 0xFF);
        }

        return result;
    }

    /**
     * Looks up the country code for an IP address using the CSV dataset
     * Note: The dataset file "asn-country-ipv4-num.csv" should be placed in the
     * resources folder src/main/resources/
     * and have the format: start_ip,end_ip,country_code
     * 
     * @param ipLong  The IP address converted to a long integer
     * @param context The Azure Functions execution context for logging
     * @return The two-letter country code or null if not found
     */
    public String lookupCountryCode(long ipLong, ExecutionContext context) {
        try {
            InputStream is = getResourceAsStream("asn-country-ipv4-num.csv");
            if (is == null) {
                context.getLogger().severe("Error reading CSV file: resource not found");
                return null;
            }

            try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                    CSVParser csvParser = CSVFormat.DEFAULT.builder().get().parse(reader)) {

                for (CSVRecord record : csvParser) {
                    if (record.size() < 3)
                        continue;

                    long startRange = Long.parseLong(record.get(0));
                    long endRange = Long.parseLong(record.get(1));

                    if (ipLong >= startRange && ipLong <= endRange) {
                        return record.get(2);
                    }
                }
                return null;
            }
        } catch (IOException e) {
            context.getLogger().severe("Error reading CSV file: " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets resource as stream - method made accessible for testing
     * 
     * @param resourceName The name of the resource
     * @return InputStream of the resource or null if not found
     */
    public InputStream getResourceAsStream(String resourceName) {
        return getClass().getClassLoader().getResourceAsStream(resourceName);
    }
}