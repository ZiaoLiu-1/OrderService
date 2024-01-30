import com.sun.net.httpserver.HttpServer;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
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



public class ProductService {
    static class ServiceConfig {
        int port;
        String ip;

        public ServiceConfig(int port, String ip) {
            this.port = port;
            this.ip = ip;
        }

        // Getters
        public int getPort() {
            return port;
        }

        public String getIp() {
            return ip;
        }
    }

    private static ServiceConfig readConfig(String filePath, String serviceName) {
        try {
            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            JSONObject jsonObject = new JSONObject(content);
            JSONObject serviceConfig = jsonObject.getJSONObject(serviceName);
            int port = serviceConfig.getInt("port");
            String ip = serviceConfig.getString("ip");
            return new ServiceConfig(port, ip);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) throws IOException {
        // Initialize SQLite Database
        String path = System.getProperty("user.dir");
        // Convert the string path to a Path object
        Path currentPath = Paths.get(path);
        // Get the parent of the current path
        Path parentPath = currentPath.getParent();
        path = parentPath.getParent().toString(); 
        ServiceConfig productServiceConfig = readConfig(path + "/config.json", "ProductService");
        if (productServiceConfig == null) {
            System.err.println("Failed to read config for ProductService. Using default settings.");
            productServiceConfig = new ServiceConfig(14000, "127.0.0.1"); // default settings
        }
        int port = productServiceConfig.getPort();
        HttpServer server = HttpServer.create(new InetSocketAddress(productServiceConfig.getIp(), port), 0);
        server.setExecutor(Executors.newFixedThreadPool(20)); 
        server.createContext("/product", new ProductHandler(server));
        server.setExecutor(null);
        server.start();

        System.out.println("Server started on port " + port);
    }
    

    
    
    private static String getDatabaseUrl() {
        String path = System.getProperty("user.dir");

        // Convert the string path to a Path object
        Path currentPath = Paths.get(path);

        // Get the parent of the current path
        path = currentPath.getParent().toString();
        String databasePath = path + "/Database/info.db";
        return "jdbc:sqlite:" + databasePath;
    }

    static class ProductHandler implements HttpHandler {
        HttpServer server;
        ProductHandler(HttpServer server){
            this.server = server;
        }
    @Override
    public void handle(HttpExchange exchange) throws IOException {

            if ("POST".equals(exchange.getRequestMethod())) {
                JSONObject requestJson = new JSONObject(getRequestBody(exchange));
                String command = requestJson.optString("command");

                switch (command) {
                    case "create":
                        createProduct(exchange, requestJson);
                        break;
                    case "update":
                        updateProduct(exchange, requestJson);
                        break;
                    case "delete":
                        deleteProduct(exchange, requestJson);
                        break;
                    case "shutdown":
                        System.out.println("Shutting down");
                        handleShutdownCommand(exchange, server);
                        break;
                    default:
                        sendResponse(exchange, "Invalid command", 400);
                        return;
                }
                sendResponse(exchange, "Product operation successful", 200);
            } else if ("GET".equals(exchange.getRequestMethod())) {
                String requestURI = exchange.getRequestURI().toString();
                    String[] uri = requestURI.split("/");
                    String idStr = "";
                    try{
                        idStr = uri[2];
                    }catch(Exception e){
                        System.err.println(e.getMessage());
                        sendResponse(exchange, "Please enter Product ID", 404);
                        exchange.close();
                    }
                    
                    
                    int id = Integer.parseInt(idStr); // Parse the string to an integer
                    String url = getDatabaseUrl();
                    
                
                    try (Connection conn = DriverManager.getConnection(url);
                         PreparedStatement query = conn.prepareStatement("SELECT * FROM products WHERE id = ?")) {
                        
                        query.setInt(1, id); // Set the id as an integer
                        ResultSet rs = query.executeQuery();
                
                        if (rs.next()) {
                            // Assuming columns like name, email, etc. Adjust according to your schema
                            String name = rs.getString("name");
                            int price = rs.getInt("price");
                            String quantity = rs.getString("quantity");
                            String description = rs.getString("description");

                
                            JSONObject respond = new JSONObject();
                            respond.put("id", id);
                            respond.put("name", name);
                            respond.put("description", description);
                            respond.put("price", price);
                            respond.put("quantity", quantity);
                            
                            sendResponse(exchange, respond.toString(), 200);
                        } else {
                            // Handle case where no product is found
                            sendResponse(exchange, "Product not found", 404);
                        }
                    } catch (SQLException e) {
                        System.err.println(e.getMessage());
                        // Send an error response to client
                        sendResponse(exchange, "Internal Server Error", 500);
                    }
                } else {
                        // Send a 405 Method Not Allowed response for non-POST requests
                        exchange.sendResponseHeaders(405, 0);
                        exchange.close();
            }
        }
    }
    private static void handleShutdownCommand(HttpExchange exchange, HttpServer server) throws IOException {
        JSONObject responseJson = new JSONObject();
        responseJson.put("command", "shutdown");
    
        // Send the shutdown command response
        sendResponse(exchange, responseJson.toString(), 200);
    
        // Perform server shutdown operations
        server.stop(4); // Gracefully stop the server with a delay of 1 second
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

    private static void createProduct(HttpExchange exchange, JSONObject json) {
        String url = getDatabaseUrl();
    
        // Check for required fields and that quantity and price are not negative
        if (!json.has("name") || json.optString("name").isEmpty() || 
            !json.has("id") || 
            !json.has("description") || json.optString("description").isEmpty() ||
            !json.has("price") || json.optDouble("price", -1.0) < 0 ||
            !json.has("quantity") || json.optInt("quantity", -1) < 0 || json.optInt("id") < 0) {
            System.err.println("Missing or invalid fields. Command refused.");
            try {
                sendResponse(exchange, "Bad Request", 400);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
    
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmtCheck = conn.prepareStatement(
                     "SELECT COUNT(*) FROM products WHERE id = ?");
             PreparedStatement pstmtInsert = conn.prepareStatement(
                     "INSERT INTO products (id, name, description, price, quantity) VALUES (?, ?, ?, ?, ?)")) {
    
            // Check if ID already exists
            pstmtCheck.setInt(1, json.getInt("id"));
            ResultSet rs = pstmtCheck.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                System.err.println("Product ID already exists. Command refused.");
                sendResponse(exchange, "Product ID already exists", 409);
                return;
            }
    
            // Insert the new product
            pstmtInsert.setInt(1, json.getInt("id"));
            pstmtInsert.setString(2, json.getString("name"));
            pstmtInsert.setString(3, json.getString("description"));
            pstmtInsert.setDouble(4, json.getDouble("price"));
            pstmtInsert.setInt(5, json.getInt("quantity"));
            pstmtInsert.executeUpdate();

            // Send the success response with the product details
            JSONObject responseJson = new JSONObject();
            responseJson.put("id", json.getInt("id"));
            responseJson.put("name", json.getString("name"));
            responseJson.put("description", json.getString("description"));
            responseJson.put("price", json.getDouble("price"));
            responseJson.put("quantity", json.getInt("quantity"));
            
            sendResponse(exchange, responseJson.toString(), 200); 

        } catch (SQLException e) {
            System.err.println(e.getMessage());
            try {
                sendResponse(exchange, "Internal Server Error", 500);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        } catch (IOException e) {
            System.err.println("IOException occurred while sending response: " + e.getMessage());
        }
    }
    



    private static void updateProduct(HttpExchange exchange, JSONObject json) {
        String url = getDatabaseUrl();
        System.out.println("Updating");
    
        // Check for the product ID, price, and quantity fields
        if (!json.has("id")) {
            System.err.println("Product ID is missing. Command refused.");
            try {
                sendResponse(exchange, "Missing product ID", 400);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        if (json.has("price") && json.optDouble("price", -1.0) < 0) {
            System.err.println("Price cannot be negative. Command refused.");
            try {
                sendResponse(exchange, "Price cannot be negative", 400);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        if (json.has("quantity") && json.optInt("quantity", -1) < 0) {
            System.err.println("Quantity cannot be negative. Command refused.");
            try {
                sendResponse(exchange, "Quantity cannot be negative", 400);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        int productId = json.getInt("id");
        if(productId < 0){
            try {
                sendResponse(exchange, "ID can't be negative", 400);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        try (Connection conn = DriverManager.getConnection(url)) {
            System.out.println("updating database");
            StringBuilder sql = new StringBuilder("UPDATE products SET ");
            List<Object> params = new ArrayList<>();
            boolean needComma = false;
    
            if (json.has("name") && !json.optString("name").isEmpty()) {
                System.out.println(json.get("name").toString());
                sql.append("name = ?");
                params.add(json.getString("name"));
                needComma = true;
            }
            if (json.has("description") && !json.optString("description").isEmpty()) {
                if (needComma) sql.append(", ");
                sql.append("description = ?");
                params.add(json.getString("description"));
                needComma = true;
            }
            if (json.has("price")) {
                if (needComma) sql.append(", ");
                sql.append("price = ?");
                params.add(json.getDouble("price"));
                needComma = true;
            }
            if (json.has("quantity")) {
                if (needComma) sql.append(", ");
                sql.append("quantity = ?");
                params.add(json.getInt("quantity"));
            }
    
            sql.append(" WHERE id = ?");
            try (PreparedStatement pstmtUpdate = conn.prepareStatement(sql.toString())) {
                int paramIndex = 1;
                for (Object param : params) {
                    pstmtUpdate.setObject(paramIndex++, param);
                }
                pstmtUpdate.setInt(paramIndex, productId);
    
                int affectedRows = pstmtUpdate.executeUpdate();
                if (affectedRows > 0) {
                    try (PreparedStatement pstmtSelect = conn.prepareStatement("SELECT * FROM products WHERE id = ?")) {
                        pstmtSelect.setInt(1, productId);
                        ResultSet rs = pstmtSelect.executeQuery();
            
                        if (rs.next()) {
                            JSONObject responseJson = new JSONObject();
                            responseJson.put("id", rs.getInt("id"));
                            responseJson.put("name", rs.getString("name"));
                            responseJson.put("description", rs.getString("description"));
                            responseJson.put("price", rs.getDouble("price"));
                            responseJson.put("quantity", rs.getInt("quantity"));
            
                            sendResponse(exchange, responseJson.toString(), 200);
                        } else {
                            sendResponse(exchange, "Product not found after update", 500);
                        }
                    }
                    
                } else {
                    sendResponse(exchange, "Product not found", 404);
                }
            }
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            try {
                sendResponse(exchange, "Internal Server Error", 500);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        } catch (IOException e) {
            System.err.println("IOException occurred while sending response: " + e.getMessage());
        }
    }
    

    private static void deleteProduct(HttpExchange exchange, JSONObject json) {
        String url = getDatabaseUrl();
        if (!json.has("id") || !json.has("name") || !json.has("price") || !json.has("quantity")) {
            System.err.println("Missing required fields. Command refused.");
            try {
                sendResponse(exchange, "Bad Request: Missing required fields", 400);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
    
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(
                     "DELETE FROM products WHERE id = ? AND name = ? AND price = ? AND quantity = ?")) {
            pstmt.setInt(1, json.getInt("id"));
            pstmt.setString(2, json.getString("name"));
            pstmt.setDouble(3, json.getDouble("price"));
            pstmt.setInt(4, json.getInt("quantity"));
            
            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows == 0) {
                System.err.println("No product found or product could not be deleted.");
                sendResponse(exchange, "", 404); // Not Found status code
            } else {
                sendResponse(exchange, "", 200); // OK status code for successful deletion
            }
            
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            try {
                sendResponse(exchange, "Internal Server Error", 500);
            } catch (IOException e1) {
                e1.printStackTrace();
            } 
        } catch (IOException e) {
            System.err.println("IOException occurred while sending response: " + e.getMessage());
        }
    }
}

    