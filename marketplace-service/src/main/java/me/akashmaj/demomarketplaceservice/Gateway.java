package me.akashmaj.demomarketplaceservice;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionStage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.CriteriaBuilder;

public class Gateway extends AbstractBehavior<Gateway.Command> {

    /* will store productId to product ActorRef mapping */
    public Map<Integer, ActorRef<Product.Command>> productsRef;

    /* will point to the PostOrder actor that spawns children Order actors */
    public ActorRef<PostOrder.Command> postOrderActor;

    /* will help to populate the product actors from excel file*/
    ProductsPopulator productsPopulator;

    /* since no db is used, we use these internal states for orders and orderitems */
    public Integer orderIdCounter = 2222;
    public Integer orderItemsCounter = 4444;

    /* actorrefs to the order actors so created are being saved */
    public static Map<Integer, ActorRef<Order.Command>> orderActorsRef;

    public static Behavior<Gateway.Command> create() {
        return Behaviors.setup(Gateway::new);
    }

    private Gateway(ActorContext<Command> context) {

        super(context);

        postOrderActor = context.spawn(PostOrder.create(getContext().getSelf()), "PostOrderActor");
        productsRef = new HashMap<>();
        orderActorsRef = new HashMap<>();
        productsPopulator = new ProductsPopulator();
        productsPopulator.processExcelFile();

        /* populate the products from excel file */
        for(int i = 0; i < productsPopulator.products; i++) {
            Integer productId = productsPopulator.productIds.get(i);
            String productName = productsPopulator.productNames.get(i);
            String productDescription = productsPopulator.productDescriptions.get(i);
            Integer productPrice = productsPopulator.productPrices.get(i);
            Integer productStockQuantity = productsPopulator.productStockQuantitys.get(i);

            ActorRef<Product.Command> productActor = context.spawn(
                    Product.create(productId, productName, productDescription, productPrice, productStockQuantity), 
                    "Product-" + productId
                );
            productsRef.put(productId, productActor);
        }
    }

    private Behavior<Command> onSaveOrder(SaveOrder saveOrder) {
        orderActorsRef.put(saveOrder.orderId, saveOrder.orderRef);
        // System.out.println("SAVED TO INTERNAL STATE: OrderID:" + saveOrder.orderId);
        Color.cyan("SAVED TO INTERNAL STATE: OrderID: %d", saveOrder.orderId);
        return this;
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(GetProduct.class, this::onGetProduct)
                .onMessage(ProductInfoResponse.class, this::onProductInfoResponse)
                .onMessage(UpdateProduct.class, this::onUpdateProduct)
                .onMessage(OrderInfo.class, this::onOrderInfo)
                .onMessage(PlaceOrder.class, this::onPlaceOrder)
                .onMessage(GetOrder.class, this::onGetOrder)
                .onMessage(SaveOrder.class, this::onSaveOrder)
                .onMessage(CancelOrder.class, this::onCancelOrder)
                .onMessage(UpdateOrder.class, this::onUpdateOrder)
                .onMessage(GetProductRef.class, this::onGetProductRef)
                .onMessage(DeleteOrder.class, this::onDeleteOrder)


                .build();
    }

    public static class UpdateOrder implements Command {
        public Integer orderId;
        public String status;
        public ActorRef<OrderInfo> replyTo;

        public UpdateOrder(Integer orderId, String status, ActorRef<OrderInfo> replyTo) {
            this.orderId = orderId;
            this.status = status;
            this.replyTo = replyTo;
        }
    }

    private Behavior<Command> onUpdateOrder(UpdateOrder updateOrder) {
        Optional<ActorRef<Order.Command>> order = Optional.ofNullable(orderActorsRef.get(updateOrder.orderId));

        if (order.isPresent()) {
            // System.out.println("Inside onUpdateOrder()");
            Color.blue("Inside onUpdateOrder()");

            order.get().tell(new Order.UpdateOrder(updateOrder.orderId, updateOrder.status, updateOrder.replyTo));

        } else {
            // System.out.println("I should not be coming here if order is valid");
            Color.red("I should not be coming here if order is valid: onUpdateOrder()");
            updateOrder.replyTo.tell(new OrderInfo(null, -1, -1, "Not Found", 0, null, null));
        }

        return this;
    }

    public static class CancelOrder implements Command {
        public  Integer orderId;
        public  ActorRef<Order.Command> orderRef;
        public  ActorRef<OrderInfo> replyTo;

        public CancelOrder(Integer orderId, ActorRef<Order.Command> orderRef, ActorRef<OrderInfo> replyTo) {
            this.orderId = orderId;
            this.orderRef = orderRef;
            this.replyTo = replyTo;
        }
    }

    private Behavior<Command> onCancelOrder(CancelOrder cancelOrder) {
        Optional<ActorRef<Order.Command>> order = Optional.ofNullable(orderActorsRef.get(cancelOrder.orderId));

        if (order.isPresent()) {
            // System.out.println("Inside onCancelOrder()");
            Color.blue("Inside onCancelOrder()");
            cancelOrder.orderRef = order.get();
            // Ask the product actor for its details
            CompletionStage<OrderInfo> orderDetails = AskPattern.ask(
                    order.get(),
                    (ActorRef<OrderInfo> replyTo) -> new Order.GetOrder(cancelOrder.orderId, replyTo),
                    Duration.ofSeconds(30),
                    getContext().getSystem().scheduler()
            );

            orderDetails.thenAccept(info -> {
                // System.out.println("INSIDE onCancelOrder");
                Color.blue("Inside onCancelOrder() thenAccept");

                if(info.orderStatus.equals("CANCELLED") || info.orderStatus.equals("DELIVERED")) {
                    cancelOrder.replyTo.tell(new OrderInfo(null, info.orderId, info.userId, info.orderStatus, info.totalPrice, info.itemsToOrder, null));
                    return;
                }

                info.itemsToOrder.forEach(item -> {
                    Integer productId = item.get("product_id");
                    Integer quantity = item.get("quantity");
                    productsRef.get(productId).tell(new Product.ProductUpdateRequest(null, productId, quantity));
//                    API.updateUserWallet(info.userId, info.totalPrice, "credit");

                });

                API.updateUserWallet(info.userId, info.totalPrice, "credit");
                order.get().tell(new Order.UpdateOrder(info.orderId, "CANCELLED", null));
                cancelOrder.replyTo.tell(new OrderInfo(null, info.orderId, info.userId, "CANCELLED", info.totalPrice, info.itemsToOrder, null));
            
            });

        } else {
            // System.out.println("Why this?");
            Color.red("I should not be coming here if order is valid: onCancelOrder()");
            cancelOrder.replyTo.tell(new OrderInfo(null, -1, -1, "Not Found", 0, null, null));
        }

        return this;
    }



    public static class SaveOrder implements Command {
        public final Integer orderId;
        public final ActorRef<Order.Command> orderRef;
        public final ActorRef<OrderInfo> replyTo;

        public SaveOrder(Integer orderId, ActorRef<Order.Command> orderRef, ActorRef<OrderInfo> replyTo) {
            this.orderId = orderId;
            this.orderRef = orderRef;
            this.replyTo = replyTo;
        }
    }

    private Behavior<Command> onGetOrder(GetOrder getOrder) {
        System.out.println("MAP SIZE:" + orderActorsRef.size());
        Optional<ActorRef<Order.Command>> order = Optional.ofNullable(orderActorsRef.get(getOrder.orderId));

        if (order.isPresent()) {
            // System.out.println("Inside onGetOrder()");
            Color.blue("Inside onGetOrder()");

            order.get().tell(new Order.GetOrder(getOrder.orderId, getOrder.replyTo));

        } else {
            // System.out.println("Why this?");
            Color.red("I should not be coming here if order is valid: onGetOrder()");
            getOrder.replyTo.tell(new OrderInfo(null, -1, -1, "Not Found", 0, null, null));
        }

        return this;
    }

    static class GetOrder implements Command {
        public final Integer orderId;
        public final ActorRef<OrderInfo> replyTo;

        public GetOrder(Integer orderId, ActorRef<OrderInfo> replyTo) {
            this.orderId = orderId;
            this.replyTo = replyTo;
        }
    }




    private Behavior<Command> onOrderInfo(OrderInfo orderInfo) {
        System.out.println("Order ID: " + orderInfo.orderId);
        System.out.println("User ID: " + orderInfo.userId);
        System.out.println("Order Status: " + orderInfo.orderStatus);
        System.out.println("Items to Order: " + orderInfo.itemsToOrder);
        if(orderInfo.orderStatus.equals("PLACED")) {
            Color.green("Order is placed: OrderID: %d", orderInfo.orderId);
            orderActorsRef.put(orderInfo.orderId, orderInfo.orderCreated );
        }
        return this;
    }


    private Behavior<Command> onGetProduct(GetProduct getProduct) {

        Optional<ActorRef<Product.Command>> product = Optional.ofNullable(productsRef.get(getProduct.productId));

        if (product.isPresent()) {
            // System.out.println("Inside onGetProduct()");
            Color.blue("Inside onGetProduct()");

            product.get().tell(new Product.GetProductById(getProduct.productId, getProduct.replyTo));

        } else {
            getProduct.replyTo.tell(new ProductInfoResponse(getContext().getSelf(), -1, "Not Found", "Not Found", 1, 1));
        }

        return this;
    }


    private Behavior<Command> onProductInfoResponse(ProductInfoResponse productInfoResponse){
        System.out.println("Product ID: " + productInfoResponse.productId);
        System.out.println("Product Name: " + productInfoResponse.productName);
        System.out.println("Product Description: " + productInfoResponse.productDescription);
        System.out.println("Product Price: " + productInfoResponse.productPrice);
        System.out.println("Product Stock Quantity: " + productInfoResponse.productStockQuantity);
        return this;
    }

    public interface Command {}

    public static class GetProduct implements Command {
        public final int productId;
        public final ActorRef<ProductInfoResponse> replyTo;

        public GetProduct(int productId, ActorRef<ProductInfoResponse> replyTo) {
            this.productId = productId;
            this.replyTo = replyTo;
        }
    }

    public static class UpdateProduct implements Command {
        public final int productId;
        public final int productStockQuantity;
        public final ActorRef<ProductInfoResponse> replyTo;

        public UpdateProduct(ActorRef<ProductInfoResponse> replyTo, Integer productId, Integer productStockQuantity) {
            this.productId = productId;
            this.productStockQuantity = productStockQuantity;
            this.replyTo = replyTo;
        }
    }

    private Behavior<Command> onUpdateProduct(UpdateProduct updateProduct) {
        Optional<ActorRef<Product.Command>> product = Optional.ofNullable(productsRef.get(updateProduct.productId));

        if (product.isPresent()) {
            // System.out.println("onUpdateProduct");
            Color.blue("Inside onUpdateProduct()");
 
            product.get().tell(new Product.ProductUpdateRequest(updateProduct.replyTo, updateProduct.productId, updateProduct.productStockQuantity));

        } else {
            updateProduct.replyTo.tell(new ProductInfoResponse(getContext().getSelf(), -1, "Not Found", "Not Found", 1, 1));
        }

        return this;

    }

//    {
//    "total_price":139500,
//    "user_id":1001,
//    "order_id":1,
//    "items": [
//              {"quantity":2,"product_id":101,"id":1},
//              {"quantity":1,"product_id":102,"id":2}
//             ],
//    "status":"PLACED"
//    }

    public static class OrderInfo implements Command {
        @JsonProperty("order_id")
        public Integer orderId;
        @JsonProperty("user_id")
        public Integer userId;
        @JsonProperty("status")
        public String orderStatus;
        @JsonProperty("total_price")
        public Integer totalPrice;
        @JsonProperty("items")
        public List<Map<String, Integer>> itemsToOrder;
        @JsonIgnore
        public final ActorRef<ProductInfoResponse> replyTo;
        @JsonIgnore
        public final ActorRef<Order.Command> orderCreated;

        public OrderInfo(ActorRef<ProductInfoResponse> replyTo,
                         Integer orderId, Integer userId, String orderStatus, Integer totalPrice,
                         List<Map<String, Integer>> itemsToOrder, ActorRef<Order.Command> orderCreated) {
            this.orderId = orderId;
            this.userId = userId;
            this.orderStatus = orderStatus;
            this.totalPrice = totalPrice;
            this.itemsToOrder = itemsToOrder;
            this.replyTo = replyTo;
            this.orderCreated = orderCreated;
        }

        public String toJson() throws Exception {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(this);
        }

    }

    public static class PlaceOrder implements Command {
        public Integer userId;
        public List<Map<String, Integer>> itemsToOrder;
        public final ActorRef<OrderInfo> replyTo;

        public PlaceOrder(Integer userId, List<Map<String, Integer>> itemsToOrder, ActorRef<OrderInfo> replyTo) {
            this.userId = userId;
            this.itemsToOrder = itemsToOrder;
            this.replyTo = replyTo;
        }
        public String toJson() throws Exception {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(this);
        }
    }

    private Behavior<Command> onPlaceOrder(PlaceOrder placeOrder) throws Exception {
        // postOrderActor.tell(new PostOrder.CreateOrder(222, placeOrder.userId, placeOrder.itemsToOrder, getContext().getSelf()));
        Color.blue("Inside onPlaceOrder() thenAccept");

        this.orderIdCounter += 1;
        List<Map<String, Integer>> itemsToOrder = placeOrder.itemsToOrder;
        for(var item: itemsToOrder) {
            // set id field in each item
            item.put("id", orderItemsCounter);
            orderItemsCounter += 1;
        }

        System.out.println("_____________________________________________________");
        System.out.println("PLACE ORDER ITEMS: for orderID: " + orderIdCounter);
        System.out.println("_____________________________________________________");
        System.out.println(placeOrder.toJson());
        System.out.println("_____________________________________________________");

        CompletionStage<OrderInfo> orderInfo = AskPattern.ask(
                postOrderActor,
                (ActorRef<OrderInfo> replyTo) -> new PostOrder.CreateOrder(orderIdCounter, placeOrder.userId, placeOrder.itemsToOrder, replyTo, null),
                Duration.ofSeconds(30),
                getContext().getSystem().scheduler()
        );

        orderInfo.thenAccept(info -> {
            // System.out.println("GOT:");
            Color.blue("Inside onPlaceOrder() thenAccept");
            try {
                System.out.println(info.toJson());
            } catch (Exception e) {
                // throw new RuntimeException(e);
            }
            placeOrder.replyTo.tell(new OrderInfo(null, info.orderId, info.userId, info.orderStatus, info.totalPrice, info.itemsToOrder, null));
        }).exceptionally(ex -> {
            Color.red("Exception in PostOrder.CreateOrder()");
            ex.printStackTrace();
            return null;
       });

    //    try{
    //     postOrderActor.tell(new PostOrder.CreateOrder(orderIdCounter, placeOrder.userId, placeOrder.itemsToOrder, null, placeOrder));

    //    }catch(Exception e){
    //         Color.red("Exception in PostOrder.CreateOrder()");
    //         e.printStackTrace();
    //         return null;

    //    }
       
        return this;
    }


    public static class ProductInfoResponse implements Command {
        @JsonProperty("id")
        public  Integer productId;
        @JsonProperty("name")
        public  String productName;
        @JsonProperty("description")
        public  String productDescription;
        @JsonProperty("price")
        public  Integer productPrice;
        @JsonProperty("stock_quantity")
        public  Integer productStockQuantity;
        @JsonIgnore
        public  ActorRef<Gateway.Command> replyTo;

        public ProductInfoResponse(ActorRef<Gateway.Command> replyTo, Integer productId, String productName, String productDescription, Integer productPrice, Integer productStockQuantity) {
            this.productId = productId;
            this.productName = productName;
            this.productDescription = productDescription;
            this.productPrice = productPrice;
            this.productStockQuantity = productStockQuantity;
            this.replyTo = replyTo;
        }

        public String toJson() throws Exception {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(this);
        }

    }

    public static class GetProductRef implements Command {
        public final Integer productId;
        public final ActorRef<ActorRef<Product.Command>> replyTo;
    
        public GetProductRef(Integer productId, ActorRef<ActorRef<Product.Command>> replyTo) {
            this.productId = productId;
            this.replyTo = replyTo;
        }
    }
    
    private Behavior<Command> onGetProductRef(GetProductRef request) {
        ActorRef<Product.Command> productRef = productsRef.get(request.productId);
        if (productRef != null) {
            request.replyTo.tell(productRef);
        } else {
            request.replyTo.tell(null);
            Color.red("Product ActorRef not found for productId: " + request.productId);
        }
        return this;
    }

    public static class DeleteOrder implements Command {
        public final Integer orderId;
        public final ActorRef<OrderInfo> replyTo;
    
        public DeleteOrder(Integer orderId, ActorRef<OrderInfo> replyTo) {
            this.orderId = orderId;
            this.replyTo = replyTo;
        }
    }

    private Behavior<Command> onDeleteOrder(DeleteOrder deleteOrder) {
        Optional<ActorRef<Order.Command>> order = Optional.ofNullable(orderActorsRef.get(deleteOrder.orderId));
    
        if (order.isPresent()) {
            Color.blue("Inside onDeleteOrder()");
    
            // Send DeleteOrder message to the respective order actor
            order.get().tell(new Order.DeleteOrder(deleteOrder.orderId));
    
            // Remove from internal map
            orderActorsRef.remove(deleteOrder.orderId);
    
            // Send response back
            deleteOrder.replyTo.tell(new OrderInfo(null, deleteOrder.orderId, null, "DELETED", 0, null, null));
        } else {
            Color.red("Order not found: " + deleteOrder.orderId);
            deleteOrder.replyTo.tell(new OrderInfo(null, -1, -1, "Not Found", 0, null, null));
        }
    
        return this;
    }
    
    
}
