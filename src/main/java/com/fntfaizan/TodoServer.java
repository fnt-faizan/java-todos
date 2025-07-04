package com.fntfaizan;
import com.sun.net.httpserver.*;
import com.google.gson.Gson; //library for JSON serialization/deserialization classes to and from JSON
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TodoServer {
    static List<Todo> todos = new ArrayList<>();
    static int counter = 1;
    static Gson gson = new Gson();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/todos", exchange -> { //lambda expression that implements HttpHandler
            String method = exchange.getRequestMethod();
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");

            if ("GET".equalsIgnoreCase(method)) { //check if the request method is GET
                // Return all todos that are not deleted
                List<Todo> visible = new ArrayList<>();
                for (Todo t : todos)
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
                t.id = counter++;
                t.deleted = false;
                todos.add(t);
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

            int id;
            try { 
                id = Integer.parseInt(parts[2]);
             }
            catch (NumberFormatException e) { 
                exchange.sendResponseHeaders(400, -1); 
                return; 
            } 

            // Find the todo with the given id
            // and check if it is not deleted
            // If not found, return 404 Not Found
            // If found, handle the request based on the method
            Todo todo = null;
            for (Todo t : todos)
                if (t.id == id && !t.deleted) { todo = t;}
            if (todo == null) { exchange.sendResponseHeaders(404, -1); return; }


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
                sendJson(exchange, 200, gson.toJson(todo));
            }  
            //GET -> return the todo
            else if ("GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 200, gson.toJson(todo));

            } 
            //DELETE -> soft-delete the todo
            else if ("DELETE".equalsIgnoreCase(method)) {
                todo.deleted = true;
                exchange.sendResponseHeaders(204, -1);
            } 
            // POST -> create a new todo if data is correct
            else if ("POST".equalsIgnoreCase(method)) {
                try {
                    Todo t = gson.fromJson(readBody(exchange), Todo.class);
                    t.id = counter++;
                    t.deleted = false;
                    todos.add(t);
                    sendJson(exchange, 201, gson.toJson(t)); // 201 Created
                } catch (Exception e) {
                    exchange.sendResponseHeaders(400, -1); // Bad Request
                }
            }
            else {
                exchange.sendResponseHeaders(405, -1);
            }
        });

        server.setExecutor(null);  // single-threaded
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
