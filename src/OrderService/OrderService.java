import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.Authenticator.Result;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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



public class OrderService {
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

        // Getters
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
    
            // Extracting OrderService configuration (if needed)
            JSONObject orderServiceConfig = json.getJSONObject("OrderService");
            int orderPort = orderServiceConfig.getInt("port");
            String orderIp = orderServiceConfig.getString("ip");
    
            // Assuming InterServiceCommunication configuration might be used for something else
            // JSONObject iscConfig = json.getJSONObject("InterServiceCommunication");
            // int iscPort = iscConfig.getInt("port");
            // String iscIp = iscConfig.getString("ip");
    
            return new ServiceConfig(userPort, productPort, orderPort, orderIp, userIp, productIp);
        } catch (Exception e) {
            System.err.println("Error reading config: " + e.getMessage());
            return null;
        }
    }
    

    public static void main(String[] args) throws IOException {
        // Initialize SQLite Database
        String path = System.getProperty("user.dir");
        ServiceConfig orderServiceConfig = readConfig(path + "/config.json");
        if (orderServiceConfig == null) {
            System.out.println("Failed to read config for OrderService. Using default settings.");
            orderServiceConfig = new ServiceConfig(8080, 8081, 8082, "127.0.0.1", "127.0.0.1","127.0.0.1");
        }
        int port = orderServiceConfig.getPort(2);
        initializeDatabase();
        HttpServer server = HttpServer.create(new InetSocketAddress(orderServiceConfig.getIp(2), port), 0);
        server.setExecutor(Executors.newFixedThreadPool(20)); 
        server.createContext("/order", new OrderHandler(orderServiceConfig));
        server.createContext("/user", new UserHandler(orderServiceConfig));
        server.createContext("/product", new ProductHandler(orderServiceConfig));
    
        server.setExecutor(null);
        server.start();

        System.out.println("Server started on port " + port);
    }
    

    private static void initializeDatabase() {
        System.out.println("Initializing database...");
        String url = getDatabaseUrl();
        Connection conn = null;
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(url);
            Statement stmt = conn.createStatement();
    
            // Create table if not exists
            String sql = "CREATE TABLE IF NOT EXISTS orders (" +
                     "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                     "product_id INTEGER NOT NULL, " +
                     "user_id INTEGER NOT NULL, " +
                     "quantity INTEGER NOT NULL, "+
                     "status TEXT NOT NULL)";
    
            stmt.execute(sql);
        } catch (ClassNotFoundException | SQLException e) {
            System.err.println(e.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException ex) {
                System.err.println(ex.getMessage());
            }
        }
    }
    
    private static String getDatabaseUrl() {
        String userHome = System.getProperty("user.dir");
        String databasePath = userHome + "/Database/info.db"; // Use File.separator for better cross-platform support
        return "jdbc:sqlite:" + databasePath;
    }

    static class OrderHandler implements HttpHandler {
        private ServiceConfig config; // Instance variable to hold the configuration
    
        // Constructor that accepts a ServiceConfig instance
        public OrderHandler(ServiceConfig config) {
            this.config = config;
        }
    
        @Override
        public void handle(HttpExchange exchange) throws IOException {  
            if ("POST".equals(exchange.getRequestMethod())) {   
                JSONObject requestJson = new JSONObject(getRequestBody(exchange));  
                String command = requestJson.optString("command");  
                    
                switch (command) {  
                    case "place order":  
                        placeOrder(requestJson, this.config, exchange); // Use the instance variable
                        break;  
                    default:    
                        sendResponse(exchange, "Invalid Request", 400); 
                        return; 
                }   
    
                sendResponse(exchange, "Success", 200);    
            } else {    
                // Send a 405 Method Not Allowed response for non-POST requests 
                exchange.sendResponseHeaders(405, 0);   
                exchange.close();   
            }   
        }   
    }
    static class UserHandler implements HttpHandler {
        private ServiceConfig config;
    
        public UserHandler(ServiceConfig config) {
            this.config = config;
        }
    
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Construct the URL for the User service
            URL url = new URL("http://" + config.getIp(0) + ":" + config.getPort(0) + exchange.getRequestURI().getPath());
            System.out.println(url);
            forwardRequest(exchange, url);
        }
    }

    
    static class ProductHandler implements HttpHandler {
        private ServiceConfig config;
    
        public ProductHandler(ServiceConfig config) {
            this.config = config;
        }
    
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Construct the URL for the Product service
            URL url = new URL("http://" + config.getIp(1) + ":" + config.getPort(1) + exchange.getRequestURI().getPath());
            System.out.println(url);
            forwardRequest(exchange, url);
        }
    }

    private static void forwardRequest(HttpExchange exchange, URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(exchange.getRequestMethod());
    
        // Forward all headers from the incoming request
        exchange.getRequestHeaders().forEach((key, value) -> connection.setRequestProperty(key, String.join(",", value)));
    
        // Only set doOutput for methods that have a body
        if ("POST".equals(exchange.getRequestMethod()) || "PUT".equals(exchange.getRequestMethod())) {
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = getRequestBody(exchange).getBytes(StandardCharsets.UTF_8);
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


    private static void placeOrder(JSONObject json, ServiceConfig config, HttpExchange exchange) {
        try {
            if (!json.has("product_id") || !json.has("user_id") || !json.has("quantity")) {
                sendResponse(exchange, "Invalid Request: Missing required fields.", 400);
                return;
            }
    
            int productId = json.getInt("product_id");
            int userId = json.getInt("user_id");
            int quantity = json.getInt("quantity");
    
            // Check if user exists by sending GET request to UserService
            boolean userExists = checkEntityExistence(config.getIp(0), config.getPort(0), "user", userId);
    
            // Check if product exists and has enough quantity
            boolean productExists = checkEntityExistence(config.getIp(1), config.getPort(1), "product", productId);
            int availableQuantity = getProductQuantity(config.getIp(1), config.getPort(1), productId);
            
            // If the user or product don't exist
            if (!userExists || !productExists) {
                sendResponse(exchange, "Invalid Request: User/Product ID does not exist.", 400);
                createOrder(json, "Invalid Request", config);
                return;
            }
    
            // If requested quantity exceeds available quantity
            if (availableQuantity < quantity) {
                sendResponse(exchange, "Exceeded quantity limit.", 400);
                createOrder(json, "Exceeded quantity limit", config);
                return;
            }
    
            int newQuantity = availableQuantity - quantity;
    
            // Update product quantity
            boolean updateSuccess = updateProductQuantity(config.getIp(1), config.getPort(1), productId, newQuantity);
            if (!updateSuccess) {
                sendResponse(exchange, "Failed to update product quantity. Command refused.", 400);
                return;
            }

            // Create the order in the database and obtain the response JSON with order details
            JSONObject orderResponse = createOrder(json, "Success", config);
        
            // Send the response back with the order details
            sendResponse(exchange, orderResponse.toString(), 200); // OK status code
        } catch (Exception e) {
            System.err.println("Error placing order: " + e.getMessage());
            try {
                sendResponse(exchange, "Internal Server Error", 500);
            } catch (IOException ioException) {
                System.err.println("Error sending error response: " + ioException.getMessage());
            }
        }
    }
    
    private static JSONObject createOrder(JSONObject json, String status, ServiceConfig config) throws SQLException {
        String url = getDatabaseUrl();
        JSONObject responseJson = new JSONObject();
    
        String insertOrderSQL = "INSERT INTO orders (product_id, user_id, quantity, status) VALUES (?, ?, ?, ?)";
    
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(insertOrderSQL, Statement.RETURN_GENERATED_KEYS)) {
    
            pstmt.setInt(1, json.getInt("product_id"));
            pstmt.setInt(2, json.getInt("user_id"));
            pstmt.setInt(3, json.getInt("quantity"));
            pstmt.setString(4, status);
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating order failed, no rows affected.");
            }
    
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    long orderId = generatedKeys.getLong(1);
                    responseJson.put("id", orderId);
                    responseJson.put("product_id", json.getInt("product_id"));
                    responseJson.put("user_id", json.getInt("user_id"));
                    responseJson.put("quantity", json.getInt("quantity"));
                    responseJson.put("status", status);
                } else {
                    throw new SQLException("Creating order failed, no ID obtained.");
                }
            }
        }
        return responseJson;
    }
    
    
    private static boolean checkEntityExistence(String ip, int port, String endpoint, int entityId) throws IOException {
        URL url = new URL("http://" + ip + ":" + port + "/" + endpoint + "/" + entityId);
        System.out.println(url);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        int responseCode = con.getResponseCode();
        con.disconnect();
        return responseCode == HttpURLConnection.HTTP_OK;
    }
    
    private static int getProductQuantity(String ip, int port, int productId) throws IOException {
        URL url = new URL("http://" + ip + ":" + port + "/product/" + productId);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
    
        if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
            con.disconnect();
            return -1; // Error case
        }
    
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();
        con.disconnect();
    
        JSONObject response = new JSONObject(content.toString());
        return response.getInt("quantity");
    }
    
    private static boolean updateProductQuantity(String ip, int port, int productId, int newQuantity) throws IOException {
        URL url = new URL("http://" + ip + ":" + port + "/product/" + productId);
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
    }
    

    
    
}

    