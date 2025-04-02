package me.akashmaj.demomarketplaceservice;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.List;
import java.util.Map;
import me.akashmaj.demomarketplaceservice.DeleteOrder;
import me.akashmaj.demomarketplaceservice.DeleteOrder.RequestDelete;

public class Order extends AbstractBehavior<Order.Command> {

   Integer orderId;
   Integer userId;
   String status;
   Integer totalPrice;
   List<Map<String, Integer>> itemsToOrder;

   public Order(ActorContext<Command> context, Integer orderId, Integer userId, String status, Integer totalPrice, List<Map<String, Integer>> itemsToOrder) {
        super(context);
        this.orderId = orderId;
        this.userId = userId;
        this.status = status;
        this.totalPrice = totalPrice;
        this.itemsToOrder = itemsToOrder;
        Color.purple("> Init OrderID: %d", orderId);
    }

    public static Behavior<Command> create(Integer orderId, Integer userId, String placed, Integer integer, List<Map<String, Integer>> itemsToOrder) {
        return Behaviors.setup(context -> new Order(context, orderId, userId, placed, integer, itemsToOrder));
   }

    public interface Command{

    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(GetOrder.class, this::onGetOrder)
                .onMessage(UpdateOrder.class, this::onUpdateOrder)
                .onMessage(DeleteOrder.class, this::onDeleteOrder)
                .onMessage(GetDeleteOrder.class, this::onGetDeleteOrder)
                .build();
    }

    private Behavior<Command> onUpdateOrder(UpdateOrder updateOrder) {
        Integer orderId = updateOrder.orderId;
        String status = updateOrder.status;
        ActorRef<Gateway.OrderInfo> replyTo = updateOrder.replyTo;
        if(this.status.equals("DELIVERED") || this.status.equals("CANCELLED")){
            Color.red("Bad Request for OrderID: %d in onUpdateOrder()", orderId);

        }else if(this.status.equals("PLACED") && (status.equals("CANCELLED") || status.equals("DELIVERED"))){
            this.status = status;
        }
        if(replyTo != null)
            replyTo.tell(new Gateway.OrderInfo(null, orderId, this.userId, this.status, this.totalPrice, this.itemsToOrder, getContext().getSelf()));
        return this;
    }


    public static class UpdateOrder implements Command{
        public Integer orderId;
        public String status;
        public ActorRef<Gateway.OrderInfo> replyTo;

        public UpdateOrder(Integer orderId, String status, ActorRef<Gateway.OrderInfo> replyTo){
            this.orderId = orderId;
            this.status = status;
            this.replyTo = replyTo;
        }
    }

    public static class GetOrder implements Command{
        public Integer orderId;
        public ActorRef<Gateway.OrderInfo> replyTo;

        public GetOrder(Integer orderId, ActorRef<Gateway.OrderInfo> replyTo){
            this.orderId = orderId;
            this.replyTo = replyTo;
        }
    }

    private Behavior<Command> onGetOrder(GetOrder getOrder) {
        Integer orderId = getOrder.orderId;
        ActorRef<Gateway.OrderInfo> replyTo = getOrder.replyTo;

        replyTo.tell(new Gateway.OrderInfo(null, orderId, this.userId, this.status, this.totalPrice, this.itemsToOrder, getContext().getSelf()));
        return this;
    }

    public static class DeleteOrder implements Command {
        public final Integer orderId;
    
        public DeleteOrder(Integer orderId) {
            this.orderId = orderId;
        }
    }
    
    private Behavior<Command> onDeleteOrder(DeleteOrder deleteOrder) {
       
        return Behaviors.stopped();
    }

    public static class GetDeleteOrder implements Command {
        public final Integer orderId;
        public ActorRef<me.akashmaj.demomarketplaceservice.DeleteOrder.Command> replyTo;
        RequestDelete request;
    
        public GetDeleteOrder(Integer orderId,  ActorRef<me.akashmaj.demomarketplaceservice.DeleteOrder.Command> replyTo, RequestDelete request) {
            this.orderId = orderId;
            this.replyTo = replyTo;
            this.request = request;

        }
    }
    
    private Behavior<Command> onGetDeleteOrder(GetDeleteOrder getDeleteOrder) {
        Integer orderId = getDeleteOrder.orderId;
        ActorRef<me.akashmaj.demomarketplaceservice.DeleteOrder.Command> replyTo = getDeleteOrder.replyTo;

        replyTo.tell(new me.akashmaj.demomarketplaceservice.DeleteOrder.OrderInfo(orderId, this.userId, this.status, this.totalPrice,
                                                                this.itemsToOrder, getDeleteOrder.request));
        return Behaviors.stopped();
    }


    
}
