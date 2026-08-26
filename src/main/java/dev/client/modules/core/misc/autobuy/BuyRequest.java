package dev.client.modules.core.misc.autobuy;

public class BuyRequest {
    public String itemName;
    public int price;

    public BuyRequest(String itemName, int price) {
        this.itemName = itemName;
        this.price = price;
    }
}