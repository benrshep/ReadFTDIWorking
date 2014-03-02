package com.example.readftdi;
import java.math.BigInteger;
import java.util.List;


public class Data {
	public String dataName;
	public int startBit;
	public int endBit;
	public String byteOrder;
	public BigInteger andMask;
	public String unit;
	public String valueType;
	public float factor;
	public int offset;
	public double minimum;
	public double maximum;
	
	public void setValues (List <String> values) {
		dataName = values.get(0);
		startBit = Integer.parseInt(values.get(1));
		endBit = Integer.parseInt(values.get(2));
		andMask = new BigInteger (values.get(3), 16);
		byteOrder = values.get(4);
		unit = values.get(5);
		valueType = values.get(6);
		factor = Float.parseFloat(values.get(7));
		offset = Integer.parseInt(values.get(8));
		minimum = Double.parseDouble(values.get(9));
		maximum = Double.parseDouble(values.get(10));

	}
}
