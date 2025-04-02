package me.akashmaj.demomarketplaceservice;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

class MarketplaceHandler implements HttpHandler {

    public ActorSystem<Gateway.Command> system;
    public ActorRef<Gateway.Command> gateway;
    public Duration askTimeout;
    public Scheduler scheduler;

    public MarketplaceHandler(ActorRef<Gateway.Command> gateway, Duration askTimeout, Scheduler scheduler) {
        this.gateway = gateway;
        this.askTimeout = askTimeout;
        this.scheduler = scheduler;
    }

    @Override
    public void handle(HttpExchange t) throws IOException {

        String path = t.getRequestURI().getPath();
        String m = t.getRequestMethod();
        System.out.println("MarketplaceHandler PATH: " + path);

        String[] parts = path.split("/");
        System.out.println("MarketplaceHandler PATH Length: " + parts.length);

        if(parts.length == 4 && parts[1].equals("marketplace") && m.equals("GET")){
            try {
                String jsonResponse = "GET NOT Implemented for /marketplace/users/{id}";
                t.getResponseHeaders().set("Content-Type", "application/json");
                t.sendResponseHeaders(200, jsonResponse.length());
                OutputStream os = t.getResponseBody();
                os.write(jsonResponse.getBytes());
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        
        }else if(parts.length == 4 && parts[1].equals("marketplace") && m.equals("DELETE")){
            try {
                String jsonResponse = "DELETE NOT Implemented for /marketplace/users/{id}";
                t.getResponseHeaders().set("Content-Type", "application/json");
                t.sendResponseHeaders(200, jsonResponse.length());
                OutputStream os = t.getResponseBody();
                os.write(jsonResponse.getBytes());
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        
        }else if(parts.length == 4 && parts[1].equals("marketplace") && m.equals("PUT")){
            try {
                String jsonResponse = "PUT NOT Implemented for /marketplace/users/{id}";
                t.getResponseHeaders().set("Content-Type", "application/json");
                t.sendResponseHeaders(200, jsonResponse.length());
                OutputStream os = t.getResponseBody();
                os.write(jsonResponse.getBytes());
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        
        }
        
        else {
            try {
                String jsonResponse = "NOT Implemented for " + path;
                t.getResponseHeaders().set("Content-Type", "application/json");
                t.sendResponseHeaders(200, jsonResponse.length());
                OutputStream os = t.getResponseBody();
                os.write(jsonResponse.getBytes());
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

