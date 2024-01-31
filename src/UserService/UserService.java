import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.json.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;


public class UserService {

    /**
     * Class to hold the configuration details for a service
     */
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


    /**
     * Returns a ServiceConfig object containing the configuration details for the specified service.
     *
     * @param filePath     Path to the config file
     * @param serviceName  Name of the service to read the config for
     * @return             ServiceConfig object containing the configuration details
     */
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


    /**
     * This method hashes the given password using SHA-256.
     * 
     * @param passwordToHash   The password to hash
     * @return                 The hashed password using SHA-256
     */
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


    /**
     * The main method of the ProductService. It starts the server and listens for requests on the specified port and ip.
     * 
     * It reads the config.json file to get the port and ip of the UserService.
     * 
     * If the config.json file does not exist or the config for the UserService does not exist, it uses the default settings.
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
        ServiceConfig userServiceConfig = readConfig(path + "/config.json", "UserService");
        if (userServiceConfig == null) {
            System.err.println("Failed to read config for UserService. Using default settings.");
            userServiceConfig = new ServiceConfig(14001, "127.0.0.1"); 
        }
        int port = userServiceConfig.getPort();
        HttpServer server = HttpServer.create(new InetSocketAddress(userServiceConfig.getIp(), port), 0);
        server.setExecutor(Executors.newFixedThreadPool(20)); 
        server.createContext("/user", new UserHandler(server));
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
     * Handler class for the UserService.
     */
    static class UserHandler implements HttpHandler {

        HttpServer server;
        UserHandler(HttpServer server) {
            this.server = server;
        }

        /**
         * This method handles the following HTTP requests:
         * 
         * For a GET request: It returns the user details for the user ID specified at the end of url if this ID exists in the database.
         * 
         * For a POST request: It performs different operations based on the command specified in the request body.
         *    - create: Creates a new user in the database if every required field is provided correctly and the provided user ID does not already exist in the database.
         *    - update: Updates the information of an existing user in the database if the provided user ID exists in the database.
         *    - delete: Deletes an existing user from the database if every required field is matched correctly.
         *    - shutdown: Shuts down the server.
         * 
         * @param exchange  The HttpExchange object
         * @throws IOException
         */
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
                    case "shutdown":
                        System.out.println("Shutting down");
                        handleShutdownCommand(exchange, server);
                        break;
                    default:
                        sendResponse(exchange, "Unknown command", 400);
                        return;
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
                    sendResponse(exchange, "Please enter User ID", 400);
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
                        sendResponse(exchange, "User not found", 400);
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
    

    /**
     * This method handles the shutdown command. It shuts down the server after a delay of 4 seconds.
     * 
     * If successfully shut down, it responds with {"command": "shutdown"} and the status code 200.
     * 
     * @param exchange  The HttpExchange object
     * @param server    The HttpServer object
     * @throws IOException
     */
    private static void handleShutdownCommand(HttpExchange exchange, HttpServer server) throws IOException {
        JSONObject responseJson = new JSONObject();
        responseJson.put("command", "shutdown");

        sendResponse(exchange, responseJson.toString(), 200);
    
        server.stop(4); 
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
     * This method creates a new user in the database if every required field is provided correctly and 
     * the provided user ID does not already exist in the database.
     * 
     * If successfully created, it responds with the information of the user and status code 200.
     * 
     * @param exchange  The HttpExchange object
     * @param json      The request body as a JSONObject
     */
    private static void createUser(HttpExchange exchange, JSONObject json) {
        System.out.println("Create");
        String url = getDatabaseUrl();
    
        // Validate required fields
        if (!json.has("username") || json.optString("username").isEmpty() ||
            !json.has("email") || json.optString("email").isEmpty() ||
            !json.has("password") || json.optString("password").isEmpty() ||
            !json.has("id") || (json.getInt("id") < 0)) {
    
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


    /**
     * This method updates the information of an user if the provided user ID does exist in the database.
     * 
     * Notice that not all of the fields are required. This method will only update the ones that are provided.
     * 
     * If successfully updated, it responds with the information of the user and status code 200.
     * 
     * @param exchange  The HttpExchange object
     * @param json      The request body as a JSONObject
     */
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
    
            if (json.has("username") && !json.optString("username").isEmpty()) {
                sql.append("username = ?");
                params.add(json.getString("username"));
                needComma = true;
            }
            if (json.has("email") && !json.optString("email").isEmpty()) {
                if (needComma) sql.append(", ");
                sql.append("email = ?");
                params.add(json.getString("email"));
                needComma = true;
            }
            if (json.has("password") && !json.optString("password").isEmpty()) {
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
                    sendResponse(exchange, "User ID does not exist", 400);
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



    /**
     * This method deletes an user in the database if every required field is matched correctly.
     * 
     * It only responds with the correct status code.
     * 
     * @param exchange  The HttpExchange object
     * @param json      The request body as a JSONObject
     */
    private static void deleteUser(HttpExchange exchange, JSONObject json) {
        String url = getDatabaseUrl();
        if (!json.has("id") || !json.has("username") || !json.has("email") || !json.has("password")) {
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
                     "DELETE FROM users WHERE id = ? AND username = ? AND email = ? AND password = ?")) {
            pstmt.setInt(1, json.getInt("id"));
            pstmt.setString(2, json.getString("username"));
            pstmt.setString(3, json.getString("email"));
            pstmt.setString(4, json.getString("password"));
    
            int affectedRows = pstmt.executeUpdate();
    
            if (affectedRows == 0) {
                System.err.println("No user found or user could not be deleted.");
                sendResponse(exchange, "", 400); 
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
