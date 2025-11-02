package sa.edu.kau.fcit.cpit490;

import com.microsoft.azure.functions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


public class FunctionTest {
    
    private ExecutionContext executionContext;
    private Logger logger;
    private Function function;
    
    @BeforeEach
    public void setup() {
        // Create mocks
        executionContext = mock(ExecutionContext.class);
        logger = mock(Logger.class);
        
        // Set up function and mocks
        function = new Function();
        when(executionContext.getLogger()).thenReturn(logger);
    }
    
    /**
     * Unit test for HttpTriggerJava method.
     */
    @Test
    @DisplayName("Test HTTP trigger with valid IP parameter")
    public void testHttpTriggerWithValidIP() throws Exception {
        // Setup
        @SuppressWarnings("unchecked")
        final HttpRequestMessage<Optional<String>> req = mock(HttpRequestMessage.class);

        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("ip", "192.168.0.1");
        doReturn(queryParams).when(req).getQueryParameters();

        final Optional<String> queryBody = Optional.empty();
        doReturn(queryBody).when(req).getBody();

        doAnswer(new Answer<HttpResponseMessage.Builder>() {
            @Override
            public HttpResponseMessage.Builder answer(InvocationOnMock invocation) {
                HttpStatus status = (HttpStatus) invocation.getArguments()[0];
                return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
            }
        }).when(req).createResponseBuilder(any(HttpStatus.class));
        Function functionSpy = spy(function);
        doReturn("US").when(functionSpy).getCountryFromIP(eq("192.168.0.1"), any(ExecutionContext.class));

        final HttpResponseMessage ret = functionSpy.run(req, executionContext);

        assertEquals(HttpStatus.OK, ret.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, String> responseBody = (Map<String, String>) ret.getBody();
        assertEquals("192.168.0.1", responseBody.get("ip"));
        assertEquals("US", responseBody.get("countryCode"));
    }

    @Test
    @DisplayName("Test HTTP trigger with missing IP parameter")
    public void testHttpTriggerWithMissingIP() {
        // Setup
        @SuppressWarnings("unchecked")
        final HttpRequestMessage<Optional<String>> req = mock(HttpRequestMessage.class);

        final Map<String, String> queryParams = new HashMap<>();
        doReturn(queryParams).when(req).getQueryParameters();

        doAnswer(new Answer<HttpResponseMessage.Builder>() {
            @Override
            public HttpResponseMessage.Builder answer(InvocationOnMock invocation) {
                HttpStatus status = (HttpStatus) invocation.getArguments()[0];
                return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
            }
        }).when(req).createResponseBuilder(any(HttpStatus.class));

        final HttpResponseMessage ret = function.run(req, executionContext);

        assertEquals(HttpStatus.BAD_REQUEST, ret.getStatus());
        assertTrue(ret.getBody().toString().contains("Please provide an IP address"));
        verify(logger).warning(anyString());
    }
    
    @Test
    @DisplayName("Test getCountryFromIP with valid IP")
    public void testGetCountryFromIP_withValidIP() throws Exception {
        Function functionSpy = spy(function);
        doReturn(3232235521L).when(functionSpy).ipToLong(anyString());

        doReturn("US").when(functionSpy).lookupCountryCode(anyLong(), eq(executionContext));
        
        String result = functionSpy.getCountryFromIP("192.168.0.1", executionContext);
        
        assertEquals("US", result);
        verify(functionSpy).ipToLong("192.168.0.1");
    }
    
    @Test
    @DisplayName("Test getCountryFromIP with exception")
    public void testGetCountryFromIP_withException() throws Exception {

        Function functionSpy = spy(function);

        doThrow(new UnknownHostException("Test exception")).when(functionSpy).ipToLong(anyString());

        String result = functionSpy.getCountryFromIP("invalid-ip", executionContext);
        
        assertNull(result);
        verify(logger).warning(contains("Error looking up IP"));
    }
    
    @Test
    @DisplayName("Test ipToLong with valid IPs")
    public void testIpToLong_withValidIPs() throws Exception {
        // Test IPv4 addresses and their expected long values
        assertEquals(2130706433L, function.ipToLong("127.0.0.1"));
        assertEquals(3232235521L, function.ipToLong("192.168.0.1"));
        assertEquals(167772160L, function.ipToLong("10.0.0.0"));
        assertEquals(0L, function.ipToLong("0.0.0.0"));
        assertEquals(4294967295L, function.ipToLong("255.255.255.255"));
    }
    
    @Test
    @DisplayName("Test ipToLong with invalid format")
    public void testIpToLong_withInvalidFormat() {
        // Test with invalid format (not enough octets)
        assertThrows(UnknownHostException.class, () -> {
            function.ipToLong("192.168.0");
        });
    }
    
    @Test
    @DisplayName("Test ipToLong with invalid octet value")
    public void testIpToLong_withInvalidOctetValue() {
        // Test with invalid octet value (> 255)
        assertThrows(UnknownHostException.class, () -> {
            function.ipToLong("192.168.0.256");
        });
    }
    
    @Test
    @DisplayName("Test lookupCountryCode with matching IP")
    public void testLookupCountryCode_withMatchingIP() throws Exception {
        Function testFunction = new Function() {
            @Override
            public InputStream getResourceAsStream(String resourceName) {
                // Create a test CSV content
                String csvContent = "3232235520,3232235775,US\n"  // 192.168.0.0 - 192.168.0.255
                                  + "2130706433,2130706433,LO\n"  // 127.0.0.1
                                  + "167772160,184549375,FR\n";   // 10.0.0.0 - 10.255.255.255
                return new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));
            }
        };

        assertEquals("US", testFunction.lookupCountryCode(3232235521L, executionContext)); // 192.168.0.1
        assertEquals("LO", testFunction.lookupCountryCode(2130706433L, executionContext)); // 127.0.0.1
        assertEquals("FR", testFunction.lookupCountryCode(167772160L, executionContext));  // 10.0.0.0

        assertNull(testFunction.lookupCountryCode(1L, executionContext)); // 0.0.0.1
    }
    
    @Test
    @DisplayName("Test lookupCountryCode with IO exception")
    public void testLookupCountryCode_withIOException() throws Exception {
        Function functionSpy = spy(function);

        doReturn(null).when(functionSpy).getResourceAsStream(anyString());

        assertNull(functionSpy.lookupCountryCode(3232235521L, executionContext)); // 192.168.0.1

        verify(logger).severe(contains("Error reading CSV file"));
    }
}
