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
        ServiceConfig userServiceConfig = readConfig(path + "/config.json", "ProductService");
        if (userServiceConfig == null) {
            System.err.println("Failed to read config for UserService. Using default settings.");
            userServiceConfig = new ServiceConfig(14000, "127.0.0.1"); // default settings
        }
        int port = userServiceConfig.getPort();
        initializeDatabase();
        HttpServer server = HttpServer.create(new InetSocketAddress(userServiceConfig.getIp(), port), 0);
        server.setExecutor(Executors.newFixedThreadPool(20)); 
        server.createContext("/product", new ProductHandler());
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
            String sql = "CREATE TABLE IF NOT EXISTS products (" +
                     "id INTEGER PRIMARY KEY, " +
                     "productname TEXT NOT NULL, " +
                     "price REAL NOT NULL, " +
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

    static class ProductHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {

            if ("POST".equals(exchange.getRequestMethod())) {
                JSONObject requestJson = new JSONObject(getRequestBody(exchange));
                String command = requestJson.optString("command");

                switch (command) {
                    case "create":
                        createProduct(requestJson);
                        break;
                    case "update":
                        updateProduct(requestJson);
                        break;
                    case "delete":
                        deleteProduct(requestJson);
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
                            String name = rs.getString("productname");
                            int price = rs.getInt("price");
                            String quantity = rs.getString("quantity");

                
                            JSONObject respond = new JSONObject();
                            respond.put("id", id);
                            respond.put("productname", name);
                            respond.put("price", price);
                            respond.put("quantity", quantity);
                
                            sendResponse(exchange, respond.toString(), 200);
                        } else {
                            // Handle case where no user is found
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

    private static void createProduct(JSONObject json) {
        String url = getDatabaseUrl();
    
        // Check for required fields
        if (!json.has("productname") || json.optString("productname").isEmpty() || 
            !json.has("id")) {
            System.err.println("Product name or ID is missing. Command refused.");
            return;
        }
    
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmtCheck = conn.prepareStatement(
                     "SELECT COUNT(*) FROM products WHERE id = ?");
             PreparedStatement pstmtInsert = conn.prepareStatement(
                     "INSERT INTO products (id, productname, price, quantity) VALUES (?, ?, ?, ?)")) {
    
            // Check if ID already exists
            pstmtCheck.setInt(1, json.getInt("id"));
            ResultSet rs = pstmtCheck.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                System.err.println("Product ID already exists. Command refused.");
                return;
            }
    
            // Insert the new product
            pstmtInsert.setInt(1, json.getInt("id"));
            pstmtInsert.setString(2, json.getString("productname"));
            pstmtInsert.setDouble(3, json.getDouble("price"));
            pstmtInsert.setInt(4, json.getInt("quantity"));
            pstmtInsert.executeUpdate();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }
    



    private static void updateProduct(JSONObject json) {
        String url = getDatabaseUrl();
    
        // Check for the product ID
        if (!json.has("id")) {
            System.err.println("Product ID is missing. Command refused.");
            return;
        }
    
        try (Connection conn = DriverManager.getConnection(url)) {
            StringBuilder sql = new StringBuilder("UPDATE products SET ");
            boolean needComma = false;
    
            if (json.has("productname")) {
                sql.append("productname = ?");
                needComma = true;
            }
            if (json.has("price")) {
                if (needComma) sql.append(", ");
                sql.append("price = ?");
                needComma = true;
            }
            if (json.has("quantity")) {
                if (needComma) sql.append(", ");
                sql.append("quantity = ?");
            }
            sql.append(" WHERE id = ?");
    
            try (PreparedStatement pstmtUpdate = conn.prepareStatement(sql.toString())) {
                int paramIndex = 1;
                if (json.has("productname")) {
                    pstmtUpdate.setString(paramIndex++, json.getString("productname"));
                }
                if (json.has("price")) {
                    pstmtUpdate.setDouble(paramIndex++, json.getDouble("price"));
                }
                if (json.has("quantity")) {
                    pstmtUpdate.setInt(paramIndex++, json.getInt("quantity"));
                }
                pstmtUpdate.setInt(paramIndex, json.getInt("id"));
    
                int affectedRows = pstmtUpdate.executeUpdate();
                if (affectedRows == 0) {
                    System.err.println("Product ID does not exist. No update performed.");
                }
            }
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }
    



    private static void deleteProduct(JSONObject json) {
        String url = getDatabaseUrl();
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(
                     "DELETE FROM products WHERE id = ?")) {
            pstmt.setInt(1, json.getInt("id"));
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                System.err.println("Product ID does not exist. No delete performed.");
            }
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }
}

    