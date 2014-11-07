package com.faceyspacies.GEMWbot;

public class GEPrice {
	private String timestamp;
	private int price;
	private String id;
	
	public GEPrice(String timestamp, int price, String id) {
		this.timestamp = timestamp;
		this.price = price;
		this.id = id;
	}
	
	public String getTimestamp() {
		return this.timestamp;
	}
	
	public int getPrice() {
		return this.price;
	}
	
	public String getId() {
		return this.id;
	}
}
