package com.faceyspacies.GEMWbot.Holders;

/**
 * A holder object to hold the result of an action and if it was successful
 * @author Ty
 *
 */
public class UpdateResult {
	
	/**
	 * The result of an action in String form
	 */
	private String result;
	
	/**
	 * Whether it was considered successful or not
	 */
	private boolean success;
	
	/**
	 * Basic constructor providing all data
	 * @param result What happened
	 * @param success Was it successful?
	 */
	public UpdateResult(String result, boolean success) {
		this.result = result;
		this.success = success;
	}
	
	/**
	 * Getter method for the result
	 * @return the result
	 */
	public String getResult() {
		return result;
	}
	
	/**
	 * Getter method for success
	 * @return the success
	 */
	public boolean getSuccess() {
		return success;
	}
	
	/**
	 * Setter method to set the result
	 * @param result the new result
	 */
	public void setResult(String result) {
		this.result = result;
	}
	
	/**
	 * Setter method to set the success
	 * @param success the new success
	 */
	public void setSuccess(boolean success) {
		this.success = success;
	}
	
}
