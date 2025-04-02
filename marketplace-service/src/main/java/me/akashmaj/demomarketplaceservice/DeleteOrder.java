package me.akashmaj.demomarketplaceservice;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionStage;

public class DeleteOrder extends AbstractBehavior<DeleteOrder.Command> {

    public static ActorRef<Gateway.Command> gateway;

    public interface Command {}


    public static class RequestDelete implements Command {
        public final Integer orderId;
        public final ActorRef<Order.Command> orderRef;
        public final ActorRef<Gateway.OrderInfo> replyTo;
        

        public RequestDelete(Integer orderId, ActorRef<Order.Command> orderRef, ActorRef<Gateway.OrderInfo> replyTo) {
            this.orderId = orderId;
            this.orderRef = orderRef;
            this.replyTo = replyTo;
        }
    }

    public static Behavior<Command> create(ActorRef<Gateway.Command> gateway) {
        return Behaviors.setup(context -> new DeleteOrder(context, gateway));
    }

    private DeleteOrder(ActorContext<Command> context,ActorRef<Gateway.Command> gateway ) {
        super(context);
        this.gateway = gateway;
        
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(RequestDelete.class, this::onRequestDelete)
                .onMessage(OrderInfo.class, this::onOrderInfo)
                .build();
    }

    public static class OrderInfo implements Command{
    
        Integer orderId;
        Integer userId;
        String status;
        Integer totalPrice;
        List<Map<String, Integer>> itemsToOrder;
        RequestDelete request;
    


        public OrderInfo(Integer orderId, Integer userId, String status, Integer totalPrice,
                 List<Map<String, Integer>> itemsToOrder, RequestDelete request
                 ){
            this.orderId = orderId;
            this.userId = userId;
            this.status = status;
            this.totalPrice = totalPrice;
            this.itemsToOrder = itemsToOrder;
            this.request = request;
        }


    }

    public Behavior<DeleteOrder.Command> onOrderInfo(OrderInfo orderInfo){

        for (var item : orderInfo.itemsToOrder) {
            Integer productId = item.get("product_id");
            Integer quantity = item.get("quantity");
        
            gateway.tell( new Gateway.UpdateProduct(null, productId, quantity));
        }

        API.updateUserWallet(orderInfo.userId, orderInfo.totalPrice, "credit");
        orderInfo.request.orderRef.tell(new Order.UpdateOrder(orderInfo.orderId, "CANCELLED", null));
        orderInfo.request.replyTo.tell(new Gateway.OrderInfo(null, orderInfo.orderId, orderInfo.userId, "CANCELLED", orderInfo.totalPrice, orderInfo.itemsToOrder, null));

        return this;
    }

    private Behavior<DeleteOrder.Command> onRequestDelete(RequestDelete request) {

        request.orderRef.tell(new Order.GetDeleteOrder(request.orderId, getContext().getSelf(),  request));

        return this;
    }
}
