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

class RootHandler implements HttpHandler {

    public ActorSystem<Gateway.Command> system;
    public ActorRef<Gateway.Command> gateway;
    public Duration askTimeout;
    public Scheduler scheduler;

    public RootHandler(ActorRef<Gateway.Command> gateway, Duration askTimeout, Scheduler scheduler) {
        this.gateway = gateway;
        this.askTimeout = askTimeout;
        this.scheduler = scheduler;
    }

    @Override
    public void handle(HttpExchange t) throws IOException {

        String path = t.getRequestURI().getPath();
        String m = t.getRequestMethod();
        System.out.println("RootHandler PATH: " + path);

        String[] parts = path.split("/");
        System.out.println("RootHandler PATH Length: " + parts.length);

        try {
            String jsonResponse = "Hello From Marketplace Service!";
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

