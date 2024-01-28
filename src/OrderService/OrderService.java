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
            int user_port = json.getInt("user_port");
            int product_port = json.getInt("product_port");
            int port = json.getInt("port");
            String ip = json.getString("ip");
            String user_ip = json.getString("user_ip");
            String product_ip = json.getString("product_ip");
            return new ServiceConfig(user_port, product_port, port, ip, user_ip, product_ip);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return null;
        }
    
    }

    public static void main(String[] args) throws IOException {
        // Initialize SQLite Database
        String path = System.getProperty("user.dir");
        ServiceConfig orderServiceConfig = readConfig(path + "/config.json");
        if (orderServiceConfig == null) {
            System.err.println("Failed to read config for OrderService. Using default settings.");
            orderServiceConfig = new ServiceConfig(8080, 8081, 8082, "127.0.0.1", "127.0.0.1","127.0.0.1");
        }
        int port = orderServiceConfig.getPort(2);
        initializeDatabase();
        HttpServer server = HttpServer.create(new InetSocketAddress(orderServiceConfig.getIp(2), port), 0);
        server.setExecutor(Executors.newFixedThreadPool(20)); 
        server.createContext("/order", new OrderHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("Server started on port " + port);
    }
    

    private static void initializeDatabase() {
        String url = getDatabaseUrl();
        Connection conn = null;
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(url);
            Statement stmt = conn.createStatement();
    
            // Create table if not exists
            String sql = "CREATE TABLE IF NOT EXISTS orders (" +
                     "id INTEGER PRIMARY KEY, " +
                     "product_id INTEGER NOT NULL, " +
                     "user_id INTEGER NOT NULL, " +
                     "quantity INTEGER NOT NULL);";
    
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
        @Override
        public void handle(HttpExchange exchange) throws IOException {  
            
            if ("POST".equals(exchange.getRequestMethod())) {   
                JSONObject requestJson = new JSONObject(getRequestBody(exchange));  
                String command = requestJson.optString("command");  
                    
                switch (command) {  
                    case "place order":  
                        placeOrder(requestJson); 
                        break;  
                    default:    
                        sendResponse(exchange, "Invalid Request", 400); 
                        return; 
                }   

                sendResponse(exchange, "Success", 200);    
            }   else {    
                    // Send a 405 Method Not Allowed response for non-POST requests 
                    exchange.sendResponseHeaders(405, 0);   
                    exchange.close();   
            }   
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


    private static void placeOrder(JSONObject json, ServiceConfig config) {
        // Check for required fields
        if (!json.has("product_id") || !json.has("user_id") || !json.has("quantity")) {
            System.err.println("Invalid Request: Missing required fields.");
            return;
        }
    
        int productId = json.getInt("product_id");
        int userId = json.getInt("user_id");
        int quantity = json.getInt("quantity");
    
        // Check if user exists by sending GET request to UserService
        boolean userExists = checkEntityExistence(config.getIp(0), config.getPort(0), "user", userId);
    
        // Check if product exists and has enough quantity by sending GET request to ProductService
        boolean productExists = checkEntityExistence(config.getIp(1), config.getPort(1), "product", productId);
        int availableQuantity = getProductQuantity(config.getIp(1), config.getPort(1), productId);
    
        // If the user or product don't exist or quantity is insufficient, do not proceed
        if (!userExists || !productExists || availableQuantity < quantity) {
            System.err.println("Order cannot be placed due to invalid user/product ID or insufficient quantity.");
            return;
        }
    
        // Update product quantity (pseudo-logic, replace with actual call to product service to update quantity)
        boolean updateSuccess = updateProductQuantity(config.getIp(1), config.getPort(1), productId, availableQuantity - quantity);
    
        if (!updateSuccess) {
            System.err.println("Failed to update product quantity. Command refused.");
            return;
        }
    
        // Insert the new order into the database (same as existing implementation)
    }
    
        private static boolean checkEntityExistence(String serviceUrl, String endpoint, int entityId) throws IOException {
            URL url = new URL(serviceUrl + "/" + endpoint + "/" + entityId);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            return con.getResponseCode() == HttpURLConnection.HTTP_OK;
        }

        private static int getProductQuantity(String serviceUrl, int productId) throws IOException {
            URL url = new URL(serviceUrl + "/product/" + productId);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");

            if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return -1; // Error case
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();

            JSONObject response = new JSONObject(content.toString());
            return response.getInt("quantity");
        }

        private static boolean updateProductQuantity(String serviceUrl, int productId, int newQuantity) throws IOException {
            URL url = new URL(serviceUrl + "/product/" + productId);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json");

            JSONObject productUpdate = new JSONObject();
            productUpdate.put("id", productId);
            productUpdate.put("quantity", newQuantity);

            try(OutputStream os = con.getOutputStream()) {
                byte[] input = productUpdate.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);           
            }

            return con.getResponseCode() == HttpURLConnection.HTTP_OK;
        }

    
    
}

    