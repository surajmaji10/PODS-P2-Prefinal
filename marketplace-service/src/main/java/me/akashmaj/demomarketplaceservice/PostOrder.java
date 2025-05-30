package me.akashmaj.demomarketplaceservice;

import akka.actor.typed.Scheduler;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import me.akashmaj.demomarketplaceservice.Gateway.PlaceOrder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.CompletableFuture;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;


public class PostOrder extends AbstractBehavior<PostOrder.Command> {

    public ActorRef<Gateway.Command> gateway;
    public Duration askTimeout;
    public Scheduler scheduler;
    public API api;

    public static Behavior<PostOrder.Command> create(ActorRef<Gateway.Command> gateway) {
        return Behaviors.setup(context -> new PostOrder(context, gateway));
    }

    public PostOrder(ActorContext<Command> context, ActorRef<Gateway.Command> gateway) {
        super(context);
        api = new API();
        this.gateway = gateway;
        this.askTimeout = Duration.ofSeconds(30);
        this.scheduler = context.getSystem().scheduler();
    }

    @Override
    public Receive<PostOrder.Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(
                        PostOrder.CreateOrder.class, this::onCreateOrder
                )
                .onMessage(
                        PostOrder.CreateOrderActor.class, this::onCreateOrderActor
                )
                .build();
    }

    private Behavior<PostOrder.Command> onCreateOrder(PostOrder.CreateOrder createOrder) {
        Integer orderId = createOrder.orderId;
        Integer userId = createOrder.userId;
        List<Map<String, Integer>> itemsToOrder = createOrder.itemsToOrder;
        ActorRef<Gateway.OrderInfo> replyTo = createOrder.replyTo;
        PlaceOrder placeOrder = createOrder.placeOrder;

        AtomicBoolean orderPlaceable = new AtomicBoolean(true);
        AtomicReference<Integer> totalOrderPrice = new AtomicReference<>(0);

        // For each product, check availability
        List<CompletionStage<Void>> updateStockTasks = itemsToOrder.stream()
                .map(item -> {
                    Integer productId = item.get("product_id");
                    Integer quantity = item.get("quantity");

                    System.out.println("Product ID => " + productId);
                    System.out.println("Quantity => " + quantity);

                    return AskPattern.ask(
                            gateway,
                            (ActorRef<Gateway.ProductInfoResponse> ref) -> new Gateway.UpdateProduct(ref, productId, -1 * quantity),
                            askTimeout,
                            scheduler
                    ).thenAccept(productInfoResponse -> {
                        System.out.println("> CURRENT STOCK for Product " + productId + " on orderID:" + orderId +" => " + productInfoResponse.productStockQuantity);
                        if (productInfoResponse.productStockQuantity < 0) {
                            Color.red("SET FALSE: onCreateOrder():UpdateProduct()");
                            orderPlaceable.set(false);
                        }
                        totalOrderPrice.updateAndGet(v -> v + productInfoResponse.productPrice * quantity);
                    }).exceptionally(ex -> {
                        Color.red("Exception in PostOrder.onCreateOrder().Gateway.UpdateProduct");
                        ex.printStackTrace();
                        return null;
                   });
                })
                .toList();

        // Wait for all stock update requests to complete before proceeding
        CompletableFuture.allOf(updateStockTasks.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++");
                    System.out.println("Order PLACEABLE: " + orderPlaceable.get() + ": OrderID:" + orderId);
                    System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++");

                    if(orderPlaceable.get()){
                        // Contact user API and wallet API to check user existence and balance
                        String user = API.getUserById(userId);
                        if (user.isEmpty()) {
                            Color.red("SET FALSE: onCreateOrder():getUserById()");
                            orderPlaceable.set(false);
                        }

                        String wallet = API.getUserWalletById(userId);
                        if (wallet.isEmpty()) {
                            Color.red("SET FALSE: onCreateOrder():getUserWalletById()");
                            orderPlaceable.set(false);
                        }

                        Integer balanceUser = API.getUserBalanceById(userId);
                        if (balanceUser < totalOrderPrice.get()) {
                            orderPlaceable.set(false);
                        }

                        Boolean discountAvailed = API.getUserDiscountById(userId, true);
                        if (!discountAvailed) {
                            totalOrderPrice.updateAndGet(v -> v * 90 / 100);
                        }

                        Boolean walletUpdated = API.updateUserWallet(userId, totalOrderPrice.get(), "debit");
                        Boolean discountTaken = API.updateUserDiscount(userId, true);

                        if (!walletUpdated || !discountTaken) {
                            Color.red("SET FALSE: onCreateOrder():updateUserWallet()updateUserDiscount");
                            orderPlaceable.set(false);

                            // Rollback wallet and discount if something goes wrong
                            if (walletUpdated) {
                                API.updateUserWallet(userId, totalOrderPrice.get(), "credit");
                            }
                            if (discountTaken) {
                                API.updateUserDiscount(userId, false);
                            }
                        }
                    }

                    if (!orderPlaceable.get()) {
                        System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++=");
                        System.out.println(" >>> PUTTING IT BACK FOR OrderID:" + orderId);
                        System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++=");
                        
                        // Rollback stock if order is not placeable
                        List<CompletionStage<Void>> rollbackTasks = itemsToOrder.stream()
                                .map(item -> {
                                    Integer productId = item.get("product_id");
                                    Integer quantity = item.get("quantity");

                                    return AskPattern.ask(
                                            gateway,
                                            (ActorRef<Gateway.ProductInfoResponse> ref) -> new Gateway.UpdateProduct(ref, productId, quantity),
                                            askTimeout,
                                            scheduler
                                    ).thenAccept(productInfoResponse -> {
                                        System.out.println("RESTORED for ProductID: " + productId + " => " + productInfoResponse.productStockQuantity);
                                        if (productInfoResponse.productStockQuantity < 0) {
                                            throw new RuntimeException("?? Critical Issue: Gateway.UpdateProduct() thenAccept??");
                                        }
                                    }).exceptionally(ex -> {
                                        Color.red("?? Exception in rollback: UpdateProduct() ??");
                                        ex.printStackTrace();
                                        return null;
                                   });
                                })
                                .toList();

                        CompletableFuture.allOf(rollbackTasks.toArray(new CompletableFuture[0]))
                                .thenRun( () ->
                                    {
                                    Color.red("############### Order is NOT placeable for OrderID:%d ###############", orderId);
                                    replyTo.tell(new Gateway.OrderInfo(null, orderId, userId, "FAILED", 0, itemsToOrder, null));
                                    }
                                );
                        return;
                    }

                    // Final order status
                    if (!orderPlaceable.get()) {
                        Color.red("############### Order is NOT placeable for OrderID:%d ###############", orderId);
                        if(replyTo != null){
                            replyTo.tell(new Gateway.OrderInfo(null, orderId, userId, "FAILED", 0, itemsToOrder, null));
                        }
                        
                    } else {
                        // create order actor and save
                        Color.green("############### Order is placeable for OrderID:%d ###############", orderId);
//                        replyTo.tell(new Gateway.OrderInfo(null, orderId, userId, "PLACED", totalOrderPrice.get(), itemsToOrder, order));
                        getContext().getSelf().tell(new CreateOrderActor(orderId, userId, totalOrderPrice.get(), itemsToOrder, replyTo, placeOrder));
                        Color.green(">>>>>> Order is placed for OrderID:%d <<<<<<<", orderId);
                    }
                }).exceptionally(ex -> {
                    Color.red("?? Exception in rollback: Wait for all stock update requests ??");
                    ex.printStackTrace();
                    return null;
               });

        return this;
    }

    public static class CreateOrderActor implements Command {
        public Integer orderId;
        public Integer userId;
        public Integer totalOrderPrice;
        public List<Map<String, Integer>> itemsToOrder;
        public ActorRef<Gateway.OrderInfo> replyTo;
        PlaceOrder placeOrder;

        public CreateOrderActor(Integer orderId, Integer userId, Integer totalOrderPrice, List<Map<String, Integer>> itemsToOrder, ActorRef<Gateway.OrderInfo> replyTo, PlaceOrder placeOrder) {
            this.orderId = orderId;
            this.userId = userId;
            this.totalOrderPrice = totalOrderPrice;
            this.itemsToOrder = itemsToOrder;
            this.replyTo = replyTo;
            this.placeOrder = placeOrder;
        }
        public String toJson() throws JsonProcessingException {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(this);
        }
    }

    private Behavior<PostOrder.Command> onCreateOrderActor(CreateOrderActor createOrderActor) throws JsonProcessingException {
        System.out.println("Creating order actor");
        System.out.println("_____________________________________________________");
        System.out.println("PLACE ORDER ITEMS: for orderID: " + createOrderActor.orderId);
        System.out.println("_____________________________________________________");
        System.out.println(createOrderActor.toJson());
        System.out.println("_____________________________________________________");
        try {
            ActorRef<Order.Command> order = getContext().spawn(
                    Order.create(createOrderActor.orderId, createOrderActor.userId, "PLACED", createOrderActor.totalOrderPrice, createOrderActor.itemsToOrder),
                    "order-" + createOrderActor.orderId
            );

            System.out.println(order);
            gateway.tell(new Gateway.SaveOrder(createOrderActor.orderId, order, createOrderActor.replyTo));

            if(createOrderActor.replyTo != null){
                createOrderActor.replyTo.tell(new Gateway.OrderInfo(null, createOrderActor.orderId, createOrderActor.userId, "PLACED", createOrderActor.totalOrderPrice, createOrderActor.itemsToOrder, order));
            }
            
        } catch (Exception e) {
            Color.red("Exception in onCreateOrderActor() for OrderID: %d", createOrderActor.orderId);
            e.printStackTrace();
        }
        return this;
    }

    public interface Command {
    }

    public static class CreateOrder implements Command {
        public Integer orderId;
        public Integer userId;
        public List<Map<String, Integer>> itemsToOrder;
        public ActorRef<Gateway.OrderInfo> replyTo;
        PlaceOrder placeOrder;

        public CreateOrder(Integer orderId, Integer userId,
                           List<Map<String, Integer>> itemsToOrder,
                           ActorRef<Gateway.OrderInfo> replyTo, PlaceOrder placeOrder ) {
            this.orderId = orderId;
            this.userId = userId;
            this.itemsToOrder = itemsToOrder;
            this.replyTo = replyTo;
            this.placeOrder = placeOrder;
        }

        public String toJson() throws JsonProcessingException {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(this);
        }
    }
}
