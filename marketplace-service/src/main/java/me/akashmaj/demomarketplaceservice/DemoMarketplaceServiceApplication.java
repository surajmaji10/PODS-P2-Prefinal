package me.akashmaj.demomarketplaceservice;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;


@SpringBootApplication
public class DemoMarketplaceServiceApplication {

     /* root actor system */
    public static ActorSystem<Gateway.Command> system;
     /* gateway will be the root actor */
    public static ActorRef<Gateway.Command> gateway;
     /* ask() will wait for maximum of this */
    static Duration askTimeout;
    /* system scheduler for actor system */
    static Scheduler scheduler;

    public static void main(String[] args) {
         /* load the conf files located in resources */
        Config config = ConfigFactory.load("application.conf");

        /* Create Actor System with cluster config */ 
        system = ActorSystem.create(Gateway.create(), "ClusterSystem", config);
        gateway = system;
        askTimeout = Duration.ofSeconds(30);
        scheduler = system.scheduler();

        /* Start HTTP server on port 8081 */
        startHttpServer();
    }

    private static void startHttpServer() {
        try {
            /* Creates a HTTP server that runs on localhost and listens to port */
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 1000);

            // Define handlers
            /* The "handle" method will receive each http request and respond to it */
            server.createContext("/", new RootHandler(gateway, askTimeout, scheduler));
            server.createContext("/products", new MyProductsHandler(gateway, askTimeout, scheduler));
            server.createContext("/orders", new MyOrdersHandler(gateway, askTimeout, scheduler));
            server.createContext("/marketplace", new MarketplaceHandler(gateway, askTimeout, scheduler));

            // Custom thread pool
            /* although not needed */
            ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                8, 16, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1000)
            );

            server.setExecutor(threadPoolExecutor);
            server.start();

            Color.green(">>> HTTP Server started on port 8081 <<<");

        } catch (IOException e) {
            Color.red(" >>>> Exception: Could not start HTTP Server at 8081 <<<<");
            e.printStackTrace();
        }
    }
}

