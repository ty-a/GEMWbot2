package com.faceyspacies.GEMWbot;

public class GEPrice {
		private String timestamp;
		private int price;
		
		public GEPrice(String timestamp, int price) {
			this.timestamp = timestamp;
			this.price = price;
		}
		
		public String getTimestamp() {
			return this.timestamp;
		}
		
		public int getPrice() {
			return this.price;
		}
}
