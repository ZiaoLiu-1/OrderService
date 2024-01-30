import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.Authenticator.Result;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
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


public class UserService {

    // Config class to hold the configuration details
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

    // Method to read and parse the config.json file for a specific service
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

    private static String hashPassword(String passwordToHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(passwordToHash.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (int i = 0; i < encodedhash.length; i++) {
                String hex = Integer.toHexString(0xff & encodedhash[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Error hashing password", ex);
        }
    }

    public static void main(String[] args) throws IOException {
        // Initialize SQLite Database
        String path = System.getProperty("user.dir");
        ServiceConfig userServiceConfig = readConfig(path + "/config.json", "UserService");
        if (userServiceConfig == null) {
            System.err.println("Failed to read config for UserService. Using default settings.");
            userServiceConfig = new ServiceConfig(14001, "127.0.0.1"); // default settings
        }
        int port = userServiceConfig.getPort();
        initializeDatabase();
        HttpServer server = HttpServer.create(new InetSocketAddress(userServiceConfig.getIp(), port), 0);
        server.setExecutor(Executors.newFixedThreadPool(20)); // Adjust the pool size as needed
        server.createContext("/user", new UserHandler(server));
        server.setExecutor(null); // creates a default executor
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
            String sql = "CREATE TABLE IF NOT EXISTS users (" +
                         "id INTEGER PRIMARY KEY, " +
                         "username TEXT NOT NULL, " +
                         "email TEXT NOT NULL, " +
                         "password TEXT NOT NULL);";
    
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

    static class UserHandler implements HttpHandler {
        HttpServer server;
        UserHandler(HttpServer server){
            this.server = server;
        }
        @Override
            public void handle(HttpExchange exchange) throws IOException {
                if ("POST".equals(exchange.getRequestMethod())) {
                    String requestBody = getRequestBody(exchange);
                    System.out.println("Received POST request with body: " + requestBody);
                    JSONObject json = new JSONObject(requestBody); // This requires a JSON library
                    String command = json.optString("command");
                    System.out.println(command);
                    switch (command) {
                        case "create":
                            createUser(exchange, json);
                            break;
                        case "update":
                            updateUser(exchange, json);
                            break;
                        case "delete":
                            deleteUser(exchange, json);
                            break;
                        default:
                            // Handle unknown command
                            break;
                    }
        
                    String response = "Command processed";
                    sendResponse(exchange, response, 200);
                } else if ("GET".equals(exchange.getRequestMethod())) {
                    String requestURI = exchange.getRequestURI().toString();
                    String[] uri = requestURI.split("/");
                    String idStr = "";
                    try{
                        idStr = uri[2];
                    }catch(Exception e){
                        System.err.println(e.getMessage());
                        sendResponse(exchange, "Please enter user", 404);
                        exchange.close();
                    }
                    
                    
                    int id = Integer.parseInt(idStr); // Parse the string to an integer
                    String url = getDatabaseUrl();
                    
                
                    try (Connection conn = DriverManager.getConnection(url);
                         PreparedStatement query = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                        
                        query.setInt(1, id); // Set the id as an integer
                        ResultSet rs = query.executeQuery();
                
                        if (rs.next()) {
                            String name = rs.getString("username");
                            String email = rs.getString("email");
                            String password = rs.getString("password");
                
                            JSONObject respond = new JSONObject();
                            respond.put("id", id);
                            respond.put("username", name);
                            respond.put("email", email);
                            respond.put("password", hashPassword(password)); 
                
                            sendResponse(exchange, respond.toString(), 200);

                        } else {
                            // Handle case where no user is found
                            sendResponse(exchange, "User not found", 404);
                        }
                    } catch (SQLException e) {
                        System.err.println(e.getMessage());
                        // Send an error response to client
                        sendResponse(exchange, "Internal Server Error", 500);
                    }
                
                }else {
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


    private static void createUser(HttpExchange exchange, JSONObject json) {
        System.out.println("Create");
        String url = getDatabaseUrl();
    
        // Validate required fields
        if (!json.has("username") || json.optString("username").isEmpty() ||
            !json.has("email") || json.optString("email").isEmpty() ||
            !json.has("password") || json.optString("password").isEmpty() ||
            !json.has("id")) {
    
            System.err.println("Username, Email, Password, or ID is empty. Command refused.");
            try {
                sendResponse(exchange, "Bad Request", 400);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
    
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmtCheck = conn.prepareStatement(
                     "SELECT COUNT(*) FROM users WHERE id = ?");
             PreparedStatement pstmtInsert = conn.prepareStatement(
                     "INSERT INTO users (id, username, email, password) VALUES (?, ?, ?, ?)")) {
    
            // Check if ID already exists
            pstmtCheck.setInt(1, json.getInt("id"));
            ResultSet rs = pstmtCheck.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                System.err.println("ID already exists. Command refused.");
                sendResponse(exchange, "User ID already exists", 409);
                return;
            }
    
            // Insert the new user 
            pstmtInsert.setInt(1, json.getInt("id"));
            pstmtInsert.setString(2, json.getString("username"));
            pstmtInsert.setString(3, json.getString("email"));
            pstmtInsert.setString(4, json.getString("password"));
            pstmtInsert.executeUpdate();
    
            // Prepare response JSON
            JSONObject responseJson = new JSONObject();
            responseJson.put("id", json.getInt("id"));
            responseJson.put("username", json.getString("username"));
            responseJson.put("email", json.getString("email"));
            responseJson.put("password", hashPassword(json.getString("password"))); 
    
            // Send response
            sendResponse(exchange, responseJson.toString(), 200);
    
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            try {
                sendResponse(exchange, "Internal Server Error", 500);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }



    private static void updateUser(HttpExchange exchange, JSONObject json) {
        String url = getDatabaseUrl();
    
        // Check for empty or missing id
        if (!json.has("id")) {
            System.err.println("ID is missing. Command refused.");
            try {
                sendResponse(exchange, "Bad Request", 400);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
    
        int userId = json.getInt("id");
    
        try (Connection conn = DriverManager.getConnection(url)) {
            // Building the SQL UPDATE statement dynamically
            StringBuilder sql = new StringBuilder("UPDATE users SET ");
            List<Object> params = new ArrayList<>();
            boolean needComma = false;
    
            if (json.has("username")) {
                sql.append("username = ?");
                params.add(json.getString("username"));
                needComma = true;
            }
            if (json.has("email")) {
                if (needComma) sql.append(", ");
                sql.append("email = ?");
                params.add(json.getString("email"));
                needComma = true;
            }
            if (json.has("password")) {
                if (needComma) sql.append(", ");
                sql.append("password = ?");
                params.add(json.getString("password")); // Hash the password
            }
            sql.append(" WHERE id = ?");
    
            try (PreparedStatement pstmtUpdate = conn.prepareStatement(sql.toString())) {
                int paramIndex = 1;
                for (Object param : params) {
                    pstmtUpdate.setObject(paramIndex++, param);
                }
                pstmtUpdate.setInt(paramIndex, userId);
    
                int affectedRows = pstmtUpdate.executeUpdate();
    
                if (affectedRows == 0) {
                    System.err.println("User ID does not exist. No update performed.");
                    sendResponse(exchange, "User ID does not exist", 404);
                    return;
                }
            }
    
            // After updating, retrieve all user details to send back
            try (PreparedStatement pstmtSelect = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                pstmtSelect.setInt(1, userId);
                ResultSet rs = pstmtSelect.executeQuery();
    
                if (rs.next()) {
                    JSONObject responseJson = new JSONObject();
                    responseJson.put("id", rs.getInt("id"));
                    responseJson.put("username", rs.getString("username"));
                    responseJson.put("email", rs.getString("email"));
                    responseJson.put("password", hashPassword(rs.getString("password")));
    
                    sendResponse(exchange, responseJson.toString(), 200);
                } else {
                    sendResponse(exchange, "User not found after update", 500);
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
            System.err.println("Error sending response: " + e.getMessage());
        }
    }




    private static void deleteUser(HttpExchange exchange, JSONObject json) {
        String url = getDatabaseUrl();
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(
                     "DELETE FROM users WHERE id = ? AND username = ? AND email = ? AND password = ?")) {
            pstmt.setInt(1, json.getInt("id"));
            pstmt.setString(2, json.getString("username"));
            pstmt.setString(3, json.getString("email"));
            pstmt.setString(4, json.getString("password"));
    
            int affectedRows = pstmt.executeUpdate();
    
            if (affectedRows == 0) {
                System.err.println("No user found or user could not be deleted.");
                sendResponse(exchange, "", 404); 
            } else {
                sendResponse(exchange, "", 200); 
            }
    
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            try {
                sendResponse(exchange, "", 500);
            } catch (IOException e1) {
                e1.printStackTrace();
            } 
        } catch (IOException e) {
            System.err.println("IOException occurred while sending response: " + e.getMessage());
        }
    }

}