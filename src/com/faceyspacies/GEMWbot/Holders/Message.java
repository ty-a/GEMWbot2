package com.faceyspacies.GEMWbot.Holders;

/**
 * Represents a message in TellBot
 * @author Ty
 *
 */
public class Message {
	
	/**
	 * Who sent the message
	 */
	private String sender;
	
	/**
	 * What the message is
	 */
	private String message;
	
	/**
	 * When the message was sent 
	 */
	private String timestamp;
	
	/**
	 * Basic constructor that provides all fields 
	 * @param sender Who sent it
	 * @param message What they sent
	 * @param timestamp When they sent it
	 */
	public Message(String sender, String message, String timestamp) {
		this.sender = sender;
		this.message = message;
		this.timestamp = timestamp;
	}
	
	/**
	 * Getter method to get who sent the message
	 * @return The sender
	 */
	public String getSender() {
		return sender;
	}
	
	/**
	 * Getter method to get the message
	 * @return The message
	 */
	public String getMessage() {
		return message;
	}
	
	/**
	 * Getter method to get the timestamp
	 * @return The timestamp
	 */
	public String getTimestamp() {
		return timestamp;
	}

}
