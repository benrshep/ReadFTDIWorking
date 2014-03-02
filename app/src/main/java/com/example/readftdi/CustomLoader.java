package com.example.readftdi;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import android.content.Context;

public class CustomLoader {
	
	public Hashtable<Integer, MessageTemplate> loadYaml (String filename, Context context) throws IOException {
		BufferedReader reader = null;
		InputStream raw = context.getAssets().open(filename);
		reader = new BufferedReader(new InputStreamReader (raw));
		String line = "";
		String classType = null;
		Boolean messageFound = false;
		Boolean dataFound = false;
		Object currentObject = null;

		List<String> messageFields = new ArrayList<String>();
		List<String> dataFields = new ArrayList<String>();
        List<Data> dataBuffer = new ArrayList<Data>();

        Hashtable<Integer, MessageTemplate> customHash = new Hashtable <Integer, MessageTemplate>();
		
		while ((line = reader.readLine()) != null) {
			if ((line.length() > 0) && (line.indexOf("#") != 0)) {
				if (line.contains("!!")) {
					classType = line.trim().split("!!")[1];
					if (classType.equals("MessageTemplate")) {
						if (messageFound) {
							if (dataFound) {
								Data currentData = new Data();
								currentData.setValues (dataFields);
								dataBuffer.add(currentData);
							}
							MessageTemplate currentMessage = new MessageTemplate();
							currentMessage.setValues (messageFields, dataBuffer);
							customHash.put(Integer.decode(messageFields.get(1)), currentMessage);
							dataBuffer = new ArrayList<Data>();
							dataFields = new ArrayList<String>();
							messageFields = new ArrayList<String>();
							dataFound = false;
						}
						currentObject = new MessageTemplate();
						messageFound = true;
					} else {
						if (dataFound) {
							((Data) currentObject).setValues (dataFields);
							dataBuffer.add((Data) currentObject);
							dataFields = new ArrayList<String>(); 
						}
						currentObject = new Data();
						dataFound = true;
					}
				} else {
					String[] parts = line.split(" : ");
				    if (classType.equals("MessageTemplate")) {
				    	messageFields.add(parts[1]);
				    } else {
				    	dataFields.add(parts[1]);
				    }
				}
			}
		}
		Data lastData = new Data();
		lastData.setValues (dataFields);
		dataBuffer.add(lastData);
		MessageTemplate lastMessage = new MessageTemplate();
		lastMessage.setValues (messageFields, dataBuffer);
		customHash.put(Integer.decode(messageFields.get(1)), lastMessage);
		reader.close();
		return customHash;
	}
}
