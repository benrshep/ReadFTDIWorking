package com.example.readftdi;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentHashMap;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.content.Context;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	
	public static D2xxManager ftD2xx = null;	// FTDI manager, which is how we get the connected devices
	static CustomLoader loader = null;			// See CustomLoader class
	static Hashtable<Integer, MessageTemplate> messageMap = null;	// Hashtable where message structures are stored
	static ConcurrentHashMap <Integer, BigInteger> messageValues = new ConcurrentHashMap<Integer, BigInteger>();
	FT_Device ftDev = null;		// Eventually will be the FTDI connected device
	int devCount = 0;
	static final int BAUDRATE = 19200;
	
	public int iavailable = 0;	// Bytes available to be read from the device
	public static final int readLength = 512; // Max bytes that can be read at a time
	byte[] readData;	// The array where the data is read to
	int bufferLimit = 2000;	// The max size of the byte buffer, may need to make slightly larger depending on the volume of messages 
	// ByteByffer can really be viewed as a Queue
    int bufferPos = 0;
    int latestSC = -1;
	byte[] inputBuffer; // Where read data is copied to, and later accessed.
	public boolean bReadThreadGoing = false; // If the read thread is currently running
	public readThread read_thread;  // Instance of the readThread class

	Handler uiHandler = new Handler();
	
	TextView batteryView; // Just the TextView used to display info for now
	Button increaseVolumeButton;
	
	int batteryID = 1337;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		batteryView = (TextView) findViewById(R.id.batteryText);
		increaseVolumeButton = (Button) findViewById(R.id.increaseVolumeButton);
		
		readData = new byte[readLength]; // Make readData the appropriate length
		inputBuffer = new byte[readLength];
		loader = new CustomLoader(); 
		try {
			messageMap = loader.loadYaml("sensorData.yaml", this); // Located in assets folder
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			ftD2xx = D2xxManager.getInstance(this);
			devCount = ftD2xx.createDeviceInfoList(this);
			if (devCount > 0) {	// If there is an open device
				ftDev = ftD2xx.openByIndex(this, 0);	// Find it and bind to it
				CustomToast ("Device found");
			} else {
				CustomToast ("No device found");
			}
		} catch (D2xxManager.D2xxException ex) {
			ex.printStackTrace();
			CustomToast ("Error");
			ftDev.close();
		}
		
		if ((ftDev != null) && (ftDev.isOpen())) { // Makes sure we found a device and it is open
			// Reset FT Device 
			ftDev.purge((byte) (0));
			ftDev.setBitMode((byte)0 , D2xxManager.FT_BITMODE_RESET); 
			 // Set Baud Rate 
			ftDev.setBaudRate(BAUDRATE); 
			// Set Data Bit , Stop Bit , Parity Bit 
			ftDev.setDataCharacteristics(D2xxManager.FT_DATA_BITS_8, 
			D2xxManager.FT_STOP_BITS_1, D2xxManager.FT_PARITY_NONE); 
			// Set Flow Control 
			ftDev.setFlowControl(D2xxManager.FT_FLOW_NONE, (byte) 0x0b, (byte) 0x0d); 
			read_thread = new readThread(handler); // Initiate the read thread
			bReadThreadGoing = true;
			read_thread.start(); // Start the thread running
		}
		
		Runnable runnable = new Runnable() {
            @Override
            public void run() {     
            	 batteryView.setText(ExtractData (batteryID));
                uiHandler.postDelayed(this, 100);
            }
        };        
        uiHandler.post(runnable);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	private void CustomToast (String message) { // Just a custom function to help with notifications and debugging
    	Context context = this;
    	CharSequence text = (CharSequence) message;
    	int duration = Toast.LENGTH_SHORT;

    	Toast toast = Toast.makeText(context, text, duration);
    	toast.show();
    }	  
	
	// Method for when increaseVolumeButton is clicked
	
	public void IncreaseVolume (View view) {
		if ((ftDev != null) && (ftDev.isOpen())) {
			String writeData = ":S117N0080;";
			byte[] outData = writeData.getBytes();
			ftDev.write(outData, writeData.length());
		}
	}
	
	public void DecreaseVolume (View view) {
		if ((ftDev != null) && (ftDev.isOpen())) {
			String writeData = ":S117N0040;";
			byte[] outData = writeData.getBytes();
			ftDev.write(outData, writeData.length());
		}
	}
	
	// Sums the bytes in a message, turning it in to one BigInteger instance
	public BigInteger SumBytes (byte[] message, int start) { 
		String hexString = "";
		try {
			for (int i = 0; i < 16; i++) 
				hexString += (char) message[i+start];
			return new BigInteger (hexString, 16);
		} catch (Exception e) {
			//CustomToast (hexString);
			return new BigInteger ("-1", 10);
		}
	}
	
	// Gets the message ID from the first two bytes
	public static int ExtractID (byte[] message) { 
		int id = 0;
		int offset = 2;
		if ((int) message[0+offset] < 58)
			id += ((int) (message[0+offset]-48)) << 8;
		else
			id += ((int) (message[0+offset]-55)) << 8;
		if ((int) message[1+offset] < 58)
			id += ((int) (message[1+offset]-48)) << 4;
		else
			id += ((int) (message[1+offset]-55)) << 4;
		if ((int) message[2+offset] < 58)
			id += ((int) (message[2+offset]-48));
		else
			id += ((int) (message[2+offset]-55));
		return id;
	}
	
	// Actually gets the info from the byte array
	public static String ExtractData (int lookupID) {
		MessageTemplate currentMessage;
		BigInteger byteSum = messageValues.get (lookupID);
		if (byteSum == null)
			return "No data yet";
		String toReturn = "";
		if (messageMap.containsKey(lookupID)) // Makes sure the given ID is in the hashtable
			currentMessage = (MessageTemplate) messageMap.get (lookupID); // Finds the correct MessageTemplate 
		else
			return "Key not found";
		for (Data d : currentMessage.dataPoints) {
			// value is the value for d, which is a specific dataPoint in the message
			double value = ((byteSum.and(d.andMask)).shiftRight(currentMessage.length*8 - 1 - d.endBit).doubleValue()); // Computes the value we want using an AND mask
			if (d.valueType.equalsIgnoreCase("signed")) { // Accounts for signed and unsigned
				int half = (int) Math.pow(2.0, (d.endBit - d.startBit + 1))/2;
				if (value > half)
					value -= half*2;
			}
			value = value* d.factor + d.offset;
			toReturn = toReturn.concat((d.dataName + " : " + value + "\n"));
		}
		return toReturn; // Return a string with the message data. Will later be changed to modify a ConcurrentHashMap
	}
	
	final Handler handler =  new Handler()
    {
		@Override
    	public void handleMessage(Message msg)
    	{
			Bundle dataBundled = msg.getData();
			byte[] data = dataBundled.getByteArray("CAN_DATA");
			int messageLength = 23;
			byte[] currentMessage;
			int currentPos = 0;
			BigInteger dataSum;
			int messageID;
			while (currentPos < data.length) { 
				currentMessage = Arrays.copyOfRange (data, currentPos, currentPos+22);
				currentPos += messageLength;
				messageID = ExtractID (currentMessage);
				dataSum = SumBytes (currentMessage, 6);
				messageValues.put(messageID, dataSum);
			}
    	}
    };

	private class readThread  extends Thread
	{
		Handler mHandler;

		readThread(Handler h){
			mHandler = h;
			this.setPriority(Thread.MAX_PRIORITY);
		}

		@Override
		public void run()
		{
			bufferPos = 0;
			byte[] partMessage = new byte[23];
			while(true == bReadThreadGoing)
			{
				try {
					Thread.sleep(5);
				} catch (InterruptedException e) {
				}

				synchronized(ftDev)
				{
					iavailable = ftDev.getQueueStatus();	// If the device has bytes waiting to be read 
					latestSC = -1;
					if (iavailable > 0){
						if(iavailable > readLength)
							iavailable = readLength;
						
						ftDev.read(readData, iavailable);
						for (int i = 0; i < iavailable; i++) {
							inputBuffer[bufferPos] = readData[i];
							if (readData[i] == (byte) ';')
								latestSC = bufferPos;
							bufferPos++;
						}
						if (latestSC > 0) {
							byte[] canData = new byte[latestSC];
							canData = Arrays.copyOf(inputBuffer, latestSC);
							Message msg = mHandler.obtainMessage();
							Bundle bundle = new Bundle(); 
							bundle.putByteArray("CAN_DATA", canData);
							msg.setData(bundle);
							mHandler.sendMessage(msg); // Passes off to the readThread, which deals with the data
							for (int j = latestSC+1; j < bufferPos; j++) {
								partMessage[j-latestSC-1] = inputBuffer[j];
							}
							Arrays.fill(inputBuffer, (byte) 0);
							bufferPos = bufferPos - latestSC-1;
							for (int j= 0; j < bufferPos; j++) {
								inputBuffer[j] = partMessage[j];
							}
						}
					} 
				}
			}
		}
	}
}
