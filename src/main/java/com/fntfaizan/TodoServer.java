package com.fntfaizan;
import com.sun.net.httpserver.*;
import com.google.gson.Gson; //library for JSON serialization/deserialization classes to and from JSON
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class TodoServer {
    static Map<String, Todo> todos = new ConcurrentHashMap<>(); // thread-safe map to store todos
    // ConcurrentHashMap allows multiple threads to read and write without blocking each other 
    static Gson gson = new Gson();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/todos", exchange -> { //lambda expression that implements HttpHandler
            String method = exchange.getRequestMethod();
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");

            if ("GET".equalsIgnoreCase(method)) { //check if the request method is GET
                // Return all todos that are not deleted
                List<Todo> visible = new ArrayList<>();
                for (Todo t : todos.values())
                    if (!t.deleted) visible.add(t);
                sendJson(exchange, 200, gson.toJson(visible)); //implemented below

            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readBody(exchange);
                Todo t;
                
                //validate the JSON body and deserialize it into a Todo object
                try {
                    t = gson.fromJson(body, Todo.class);
                } catch (Exception e) {
                    exchange.sendResponseHeaders(400, -1); // Bad Request
                    return;
                }
                t.id = UUID.randomUUID().toString();
                t.deleted = false;
                todos.put(t.id, t);
                sendJson(exchange, 201, gson.toJson(t)); //201 Created

            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        });

        /* 
         * This part handles requests to /todos/{id}
         *  where {id} is the ID of the todo item.
         * It supports GET, PUT, and DELETE methods.
         */

        server.createContext("/todos/", exchange -> {
            String method = exchange.getRequestMethod();
            
            /*This gets the id of the post */
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/"); //split the url at '/' to get the parts
            if (parts.length < 3 || parts[2].isEmpty()) {
                 exchange.sendResponseHeaders(404, -1); return;
                } 

            String id = parts[2];

            // Find the todo with the given id
            // and check if it is not deleted
            // If not found, return 404 Not Found
            // If found, handle the request based on the method
            Todo todo = todos.get(id);
            if (todo == null || todo.deleted) { exchange.sendResponseHeaders(404, -1); return; }

            //PUT -> update the todo
            if ("PUT".equalsIgnoreCase(method)) {
                Todo upd;
                try {
                    upd = gson.fromJson(readBody(exchange), Todo.class);
                } catch (Exception e) {
                    exchange.sendResponseHeaders(400, -1); // Bad Request
                    return;
                }
                todo.title = upd.title;
                todo.status = upd.status;
                todos.put(todo.id, todo);
                sendJson(exchange, 200, gson.toJson(todo));
            }  
            //GET -> return the todo
            else if ("GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 200, gson.toJson(todo));

            } 
            //DELETE -> soft-delete the todo
            else if ("DELETE".equalsIgnoreCase(method)) {
                todo.deleted = true;
                todos.put(todo.id, todo);
                exchange.sendResponseHeaders(204, -1);
            } 
            // POST -> create a new todo if data is correct
            else if ("POST".equalsIgnoreCase(method)) {
                try {
                    Todo t = gson.fromJson(readBody(exchange), Todo.class);
                    t.id = UUID.randomUUID().toString();
                    t.deleted = false;
                    todos.put(t.id, t);
                    sendJson(exchange, 201, gson.toJson(t)); // 201 Created
                } catch (Exception e) {
                    exchange.sendResponseHeaders(400, -1); // Bad Request
                }
            }
            else {
                exchange.sendResponseHeaders(405, -1);
            }
        });

        server.setExecutor(Executors.newFixedThreadPool(10)); // Create a thread pool with 10 threads
        // This allows the server to handle multiple requests concurrently
        server.start();
        System.out.println("Listening on http://localhost:8080");
    }

    // Reads the body of the request and returns it as a String
    // This is used to read the JSON body of the request
    // It reads all bytes from the request body and converts them to a String
    static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    // Sends a JSON response with the given status code and JSON body
    // It sets the response headers to indicate that the content type is JSON
    // and writes the JSON body to the response output stream
    static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] resp = json.getBytes(StandardCharsets.UTF_8); //bytestream to be sent in responsen
        ex.sendResponseHeaders(code, resp.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(resp);
        }
    }
}
