import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.json.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.net.URL;
import java.net.URI;


public class OrderService {

    static boolean isFirstCommandReceived = false;

    /**
     * Class to hold the configuration details for a service
     */
    static class ServiceConfig {
        int user_port;
        int product_port;
        int port;
        String ip;
        String user_ip;
        String product_ip;

        public ServiceConfig(int user, int product, int port, String ip, String user_ip, String product_ip) {
            this.user_port = user;
            this.product_port = product;
            this.ip = ip;
            this.port = port;
            this.user_ip = user_ip;
            this.product_ip = product_ip;
        }

        public int getPort(int service) {
            if(service == 0){
                return this.user_port;
            }
            else if (service == 1){
                return this.product_port;
            
            }
            return port;
        }

        public String getIp(int service) {
            if(service == 0){
                return this.user_ip;
            }
            else if (service == 1){
                return this.product_ip;
            }
            return ip;
            
        }
    }


    /**
     * This method reads the configuration from a JSON file.
     * 
     * @param filePath The path to the JSON file
     * @return A ServiceConfig instance
     */
    private static ServiceConfig readConfig(String filePath) {

        try {
            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            JSONObject json = new JSONObject(content);
    
            // Extracting UserService configuration
            JSONObject userServiceConfig = json.getJSONObject("UserService");
            int userPort = userServiceConfig.getInt("port");
            String userIp = userServiceConfig.getString("ip");
    
            // Extracting ProductService configuration
            JSONObject productServiceConfig = json.getJSONObject("ProductService");
            int productPort = productServiceConfig.getInt("port");
            String productIp = productServiceConfig.getString("ip");
    
            // Extracting OrderService configuration 
            JSONObject orderServiceConfig = json.getJSONObject("OrderService");
            int orderPort = orderServiceConfig.getInt("port");
            String orderIp = orderServiceConfig.getString("ip");
    
            return new ServiceConfig(userPort, productPort, orderPort, orderIp, userIp, productIp);

        } catch (Exception e) {
            System.err.println("Error reading config: " + e.getMessage());
            return null;
        }
    }
    

    /**
     * The main method of the OrderService. It starts the server and listens for requests on the specified port and ip.
     * 
     * It reads the config.json file to get the port and ip of the OrderService.
     * 
     * If the config.json file does not exist or the config for the OrderService does not exist, it uses the default settings.
     * 
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        // Initialize SQLite Database
        String path = System.getProperty("user.dir");

        // Convert the string path to a Path object
        Path currentPath = Paths.get(path);

        // Get the parent of the current path
        Path parentPath = currentPath.getParent();
        path = parentPath.getParent().toString();  
        ServiceConfig orderServiceConfig = readConfig(path + "/config.json");
        if (orderServiceConfig == null) {
            System.out.println("Failed to read config for OrderService. Using default settings.");
            orderServiceConfig = new ServiceConfig(8080, 8081, 8082, "127.0.0.1", "127.0.0.1","127.0.0.1");
        }
        String url = getDatabaseUrl();
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
                initTables(conn);
             }catch(SQLException e){
                System.err.println(e.getMessage());
             }
        int port = orderServiceConfig.getPort(2);
        HttpServer server = HttpServer.create(new InetSocketAddress(orderServiceConfig.getIp(2), port), 0);
        server.setExecutor(Executors.newFixedThreadPool(20)); 
        server.createContext("/order", new OrderHandler(orderServiceConfig, server));
        server.createContext("/user", new UserHandler(orderServiceConfig));
        server.createContext("/product", new ProductHandler(orderServiceConfig));
    
        server.setExecutor(null);
        server.start();

        System.out.println("Server started on port " + port);
    }
    
    
    /**
     * Returns the database URL for the SQLite database.
     * 
     * @return  The database URL
     */
    private static String getDatabaseUrl() {
        String path = System.getProperty("user.dir");

        // Convert the string path to a Path object
        Path currentPath = Paths.get(path);

        // Get the parent of the current path
        path = currentPath.getParent().toString();
        String databasePath = path + "/Database/info.db";
        return "jdbc:sqlite:" + databasePath;
    }


    /**
     * Handler class for the OrderService.
     */
    static class OrderHandler implements HttpHandler {
        private ServiceConfig config; 
        private HttpServer server;
    
        public OrderHandler(ServiceConfig config, HttpServer server) {
            this.config = config;
            this.server = server;
        }
    

        /**
         * This method handles the following HTTP requests:
         * 
         * For a POST request: It performs different operations based on the command specified in the request body.
         *    - place order: Creates a new order in the database if every field is provided correctly and value 
         *      of the quantity field does not exceed the available quantity.
         *    - shutdown: Shuts down the server.
         *    - restart: Restarts the server.
         * @param exchange  The HttpExchange object
         * @throws IOException
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {  
            if ("POST".equals(exchange.getRequestMethod())) {   
                JSONObject requestJson = new JSONObject(getRequestBody(exchange));  
                String command = requestJson.optString("command");  
                System.out.println(command);
                if (!isFirstCommandReceived) {
                    isFirstCommandReceived = true; 

                    if (!"restart".equalsIgnoreCase(command)) {
                        // If the first command is not 'restart', drop all tables
                        System.out.println("First command");
                        dropAllTables();
                        sendResponse(exchange, "restarted", 200);
                    }
                }
                    
                switch (command) {  
                    case "place order":  
                        System.out.println("Place Order");
                        placeOrder(requestJson, this.config, exchange); 
                        break;  
                    case "shutdown":
                        System.out.println("Shutting down");
                        sendShutdownCommand(config.getIp(0), config.getPort(0), "user"); 
                        sendShutdownCommand(config.getIp(1), config.getPort(1), "product"); 
                        sendResponse(exchange, "Shutting down", 200);
                        server.stop(4);
                    case "restart":
                        break;
                    default:    
                        sendResponse(exchange, "Invalid Request", 400); 
                        return; 
                }   
    
                sendResponse(exchange, "Success", 200);    
            } else {    
                exchange.sendResponseHeaders(400, 0);   
                exchange.close();   
            }   
        }   
    }


    /**
     * This method drops all tables in the database and re-initializes them.
     */
    private static void dropAllTables() {
        System.out.println("Dropping all tables...");
        String url = getDatabaseUrl();
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            
            stmt.executeUpdate("DROP TABLE IF EXISTS orders;");
            stmt.executeUpdate("DROP TABLE IF EXISTS users;");
            stmt.executeUpdate("DROP TABLE IF EXISTS products;");
        
            // Initialize tables after dropping them
            initTables(conn); // Pass the existing connection to initTables
        } catch (SQLException e) {
            System.err.println("Error dropping tables: " + e.getMessage());
        }
    }
    

    /**
     * This method initializes the tables in the database.
     * 
     * @param conn  The connection to the database
     */
    private static void initTables(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            // Fixed SQL statement with the added comma
            String sql = "CREATE TABLE IF NOT EXISTS products (" +
                         "id INTEGER PRIMARY KEY, " +
                         "name TEXT NOT NULL, " +
                         "description TEXT NOT NULL, " + // Added comma here
                         "price REAL NOT NULL, " +
                         "quantity INTEGER NOT NULL);";
            stmt.execute(sql);
    
            sql = "CREATE TABLE IF NOT EXISTS orders (" +
                  "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                  "product_id INTEGER NOT NULL, " +
                  "user_id INTEGER NOT NULL, " +
                  "quantity INTEGER NOT NULL, " +
                  "status TEXT NOT NULL);";
            stmt.execute(sql);
    
            sql = "CREATE TABLE IF NOT EXISTS users (" +
                  "id INTEGER PRIMARY KEY, " +
                  "username TEXT NOT NULL, " +
                  "email TEXT NOT NULL, " +
                  "password TEXT NOT NULL);";
            stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("Error initializing tables: " + e.getMessage());
        }
    }
    
    
    /**
     * This method sends the shutdown command to a specified service.
     * 
     * @param ip        The IP address of the service
     * @param port      The port of the service
     * @param service   The name of the service
     */
    private static void sendShutdownCommand(String ip, int port, String service) {
        try {
            URL url = new URL("http://" + ip + ":" + port + "/" + service);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json");
    
            JSONObject shutdownCommand = new JSONObject();
            shutdownCommand.put("command", "shutdown");
    
            try (OutputStream os = con.getOutputStream()) {
                byte[] input = shutdownCommand.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
    
            int responseCode = con.getResponseCode();
            System.out.println("Shutdown command sent with response code: " + responseCode);
    
            con.disconnect();
        } catch (Exception e) {
            System.err.println("Error sending shutdown command: " + e.getMessage());
        }
    }
    

    /**
     * Handler class for the UserService.
     */
    static class UserHandler implements HttpHandler {
        private ServiceConfig config;

        public UserHandler(ServiceConfig config) {
            this.config = config;
        }

        /**
         * This method handles the HTTP requests for the UserService. It will forward the request to the UserService.
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestBody = getRequestBody(exchange); 
            JSONObject requestJson = new JSONObject(requestBody); 
            String command = requestJson.optString("command");

            if (!isFirstCommandReceived) {
                isFirstCommandReceived = true;

                if (!"restart".equalsIgnoreCase(command)) {
                    dropAllTables();
                }
            }

            try {
                URI uri = new URI("http", null, config.getIp(0), config.getPort(0), exchange.getRequestURI().getPath(), null, null);
                URL url = uri.toURL();
                forwardRequest(exchange, url, requestBody);
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }
    }

    
    /**
     * Handler class for the ProductService.
     */
    static class ProductHandler implements HttpHandler {
        private ServiceConfig config;
    
        public ProductHandler(ServiceConfig config) {
            this.config = config;
        }
        
        /**
         * This method handles the HTTP requests for the ProductService. It will forward the request to the ProductService.
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestBody = getRequestBody(exchange); 
            JSONObject requestJson = new JSONObject(requestBody); 
            String command = requestJson.optString("command");
    
            if (!isFirstCommandReceived) {
                isFirstCommandReceived = true;
    
                if (!"restart".equalsIgnoreCase(command)) {
                    dropAllTables();
                }
            }
    
            try {
                URI uri = new URI("http", null, config.getIp(1), config.getPort(1), exchange.getRequestURI().getPath(), null, null);
                URL url = uri.toURL();
                forwardRequest(exchange, url, requestBody);
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }
    }
    

    /**
     * This method forwards the HTTP request to the specified URL.
     * 
     * @param exchange      The HttpExchange object
     * @param url           The URL to forward the request to
     * @param requestBody   The request body
     * @throws IOException
     */
    private static void forwardRequest(HttpExchange exchange, URL url, String requestBody) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(exchange.getRequestMethod());
    
        // Forward all headers from the incoming request
        exchange.getRequestHeaders().forEach((key, value) -> connection.setRequestProperty(key, String.join(",", value)));
    
        // Only set doOutput for methods that have a body
        if ("POST".equals(exchange.getRequestMethod())) {
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
        }
    
        int responseCode = connection.getResponseCode();
        InputStream inputStream = responseCode < HttpURLConnection.HTTP_BAD_REQUEST ? connection.getInputStream() : connection.getErrorStream();
    
        // Forward response headers and body
        exchange.getResponseHeaders().clear();
        connection.getHeaderFields().forEach((key, values) -> {
            if (key != null && values != null) {
                values.forEach(value -> exchange.getResponseHeaders().add(key, value));
            }
        });
    
        exchange.sendResponseHeaders(responseCode, 0);
        try (OutputStream os = exchange.getResponseBody()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = inputStream.read(buffer)) != -1) {
                os.write(buffer, 0, count);
            }
        } finally {
            connection.disconnect();
        }
    }
    
    
    /**
     * This method reads the request body from the given HttpExchange object and returns it as a string.
     * 
     * @param exchange  The HttpExchange object
     * @return          The request body as a string
     * @throws IOException
     */
    private static String getRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                requestBody.append(line);
            }
            return requestBody.toString();
        }
    }


    /**
     * This method sends the given response string to the client.
     * 
     * @param exchange  The HttpExchange object
     * @param response  The response string
     * @param code      The response code
     * @throws IOException
     */
    private static void sendResponse(HttpExchange exchange, String response, int code) throws IOException {
        if( code == 200){
        exchange.sendResponseHeaders(200, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes(StandardCharsets.UTF_8));
        os.close();}
        else{
            exchange.sendResponseHeaders(code, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes(StandardCharsets.UTF_8));
            os.close();
        }
    }


    /**
     * This method creates a new order in the database if every required field is provided correctly.
     * 
     * For the product ID and user ID, they must exist in the database.
     * For the quantity, it must not exceed the available quantity of the product.
     * 
     * No matter the order is created successfully or not, it will response with the information of the order and the corresponding status code.
     * 
     * @param exchange  The HttpExchange object
     * @param json      The request body as a JSONObject
     */
    private static void placeOrder(JSONObject json, ServiceConfig config, HttpExchange exchange) {
        try {
            if (!json.has("product_id") || !json.has("quantity")) {
                sendResponse(exchange, "Invalid Request: Missing required fields.", 400);
                return;
            }
            int productId = json.getInt("product_id");
            int userId = 1;
            int quantity = json.getInt("quantity");
            if (json.has("user_id")){
                userId = json.getInt("user_id");
            }
    
            // Check if user exists by sending GET request to UserService
            boolean userExists = checkEntityExistence(config.getIp(0), config.getPort(0), "user", userId);
    
            // Check if product exists and has enough quantity
            boolean productExists = checkEntityExistence(config.getIp(1), config.getPort(1), "product", productId);
            int availableQuantity = getProductQuantity(config.getIp(1), config.getPort(1), productId);
            
            // If the user or product don't exist
            if (!userExists || !productExists) {
                sendResponse(exchange, "Invalid Request: User/Product ID does not exist.", 400);
                createOrder(json, "Invalid Request");
                return;
            }
    
            // If requested quantity exceeds available quantity
            if (availableQuantity < quantity) {
                sendResponse(exchange, "Exceeded quantity limit.", 400);
                createOrder(json, "Exceeded quantity limit");
                return;
            }
    
            int newQuantity = availableQuantity - quantity;
    
            // Update product quantity
            boolean updateSuccess = updateProductQuantity(config.getIp(1), config.getPort(1), productId, newQuantity);
            createOrder(json, "Success");
            if (!updateSuccess) {
                sendResponse(exchange, "Failed to update product quantity. Command refused.", 400);
                return;
            }
            
            sendResponse(exchange, "Order placed successfully.", 200);
        } catch (Exception e) {
            System.err.println("Error placing order: " + e.getMessage());
            try {
                sendResponse(exchange, "Internal Server Error", 500);
            } catch (IOException ioException) {
                System.err.println("Error sending error response: " + ioException.getMessage());
            }
        }
    }
    
    private static void createOrder(JSONObject json, String status) {
        String url = getDatabaseUrl();
    
        // SQL statement to insert a new order
        String insertOrderSQL = "INSERT INTO orders (product_id, user_id, quantity, status) VALUES (?, ?, ?, ?)";
    
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(insertOrderSQL, Statement.RETURN_GENERATED_KEYS)) {
    
            // Set values for the insert statement
            pstmt.setInt(1, json.getInt("product_id"));
            pstmt.setInt(2, json.getInt("user_id"));
            pstmt.setInt(3, json.getInt("quantity"));
            pstmt.setString(4, status); 
            System.out.println(pstmt);
            // Execute the insert statement
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating order failed, no rows affected.");
            }
    
            // Retrieve the generated order ID
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    long orderId = generatedKeys.getLong(1);
                    // Construct a JSON response with the order details
                    JSONObject responseJson = new JSONObject();
                    responseJson.put("id", orderId);
                    responseJson.put("product_id", json.getInt("product_id"));
                    responseJson.put("user_id", json.getInt("user_id"));
                    responseJson.put("quantity", json.getInt("quantity"));
                    responseJson.put("status", status);
                } else {
                    throw new SQLException("Creating order failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            System.err.println("SQLException: " + e.getMessage());
        }
    }
    
    
    private static boolean checkEntityExistence(String ip, int port, String endpoint, int entityId) throws IOException {
        try {
            URI uri = new URI("http", null, ip, port, "/" + endpoint + "/" + entityId, null, null);
            URL url = uri.toURL();
            System.out.println(url);
    
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            int responseCode = con.getResponseCode();
            con.disconnect();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Exception e) { // Catch URISyntaxException and IOException
            throw new IOException("Error constructing URL or opening connection", e);
        }
    }
    

    private static int getProductQuantity(String ip, int port, int productId) throws IOException {
        try {
            URI uri = new URI("http", null, ip, port, "/product/" + productId, null, null);
            URL url = uri.toURL();
    
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
    
            if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
                con.disconnect();
                return -1; // Error case
            }
    
            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
    
                JSONObject response = new JSONObject(content.toString());
                return response.getInt("quantity");
            } finally {
                con.disconnect();
            }
        } catch (Exception e) { // Catch URISyntaxException, IOException, JSONException
            throw new IOException("Error fetching product quantity", e);
        }
    }
    
    
    private static boolean updateProductQuantity(String ip, int port, int productId, int newQuantity) throws IOException {
        try {
            URI uri = new URI("http", null, ip, port, "/product/" + productId, null, null);
            URL url = uri.toURL();
    
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json");
    
            JSONObject productUpdate = new JSONObject();
            productUpdate.put("command", "update"); // Include the command field
            productUpdate.put("id", productId);
            productUpdate.put("quantity", newQuantity);
    
            try (OutputStream os = con.getOutputStream()) {
                byte[] input = productUpdate.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
    
            int responseCode = con.getResponseCode();
            con.disconnect();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Exception e) { // Catch URISyntaxException, IOException, JSONException
            throw new IOException("Error updating product quantity", e);
        }
    }
    
}

    