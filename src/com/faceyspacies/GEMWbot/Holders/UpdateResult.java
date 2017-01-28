package com.faceyspacies.GEMWbot.Holders;

public class UpdateResult {
	private String result;
	private boolean success;
	
	public UpdateResult(String result, boolean success) {
		this.result = result;
		this.success = success;
	}
	
	public String getResult() {
		return result;
	}
	
	public boolean getSuccess() {
		return success;
	}
	
	public void setResult(String result) {
		this.result = result;
	}
	
	public void setSuccess(boolean success) {
		this.success = success;
	}
	
}