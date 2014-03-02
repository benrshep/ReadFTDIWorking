package com.example.readftdi;

import java.util.List;

public class MessageTemplate {
	public String sensorName;
	public int id;
	public int length;
	public List <Data> dataPoints;
	
	public void setValues (List <String> values, List <Data> dp) {
		sensorName = values.get(0);
		id = Integer.decode(values.get(1));
		length = Integer.parseInt(values.get(2));
		dataPoints = dp;
	}
}
