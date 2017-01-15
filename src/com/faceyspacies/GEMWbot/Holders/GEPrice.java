package com.faceyspacies.GEMWbot.Holders;

public class GEPrice {
	private String timestamp;
	private String price;
	private String id;
	
	public GEPrice(String timestamp, int price, String id) {
		this.timestamp = timestamp;
		this.price = "" + price;
		this.id = id;
	}
	
	// This is used with the TradeIndexes
	public GEPrice(String timestamp, String price) {
		this.timestamp = timestamp;
		this.price = price;
	
		this.id = null;
	}
	
	public String getTimestamp() {
		return this.timestamp;
	}
	
	public String getPrice() {
		return this.price;
	}
	
	public String getId() {
		return this.id;
	}
}
