package birdbrain.finchandHummingbirdServer;

import edu.cmu.ri.createlab.hummingbird.Hummingbird;
import edu.cmu.ri.createlab.hummingbird.HummingbirdFactory;

/** Hummingbird servlet wrapper class, for use with Hummingbird servers */
public class HummingbirdServletWrapper {

	private Hummingbird hummingbird = null;
	private boolean isConnected; // Flag that is true if a hummingbird is discovered

	private int[] sensors;
	private Thread sensorLoop;
	public boolean getConnected() {
		return isConnected;
	}
	/* Get sensor data in a loop that runs at ~25 Hz */
	private class SensorLoop implements Runnable {
		public void run() {
			while(isConnected) {
				try {
					if(hummingbird != null) 
					{
						// The hummingbird call takes 8 ms, then sleep to allow other stuff to happen
						try {
							sensors = hummingbird.getState().getAnalogInputValues();
							Thread.sleep(32);
						}
						catch (NullPointerException ex) { 
							sensors = null;
						}
						if (sensors == null) {
							isConnected = false;
						}

					}
				}
				catch (InterruptedException ex) {
			        Thread.currentThread().interrupt(); // very important - causes the second interrupt to be thrown
			        break;
			     }
				
			}

		}
	}
	
	public HummingbirdServletWrapper()
	{
		
	}
	
	public boolean connect()
	{
        if(!isConnected) {
			hummingbird =  HummingbirdFactory.createHidHummingbird(); // Try to connect to Hummingbird without blocking
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
            }
            
            if(hummingbird != null) {
                isConnected = true;
                sensorLoop = new Thread(new SensorLoop()); // Start reading sensors
                sensorLoop.start();
                return true;
            }
		}
        else if (isConnected) {
        	return true;
        }
        //isConnected = false;
		return false;
	}
	
	public boolean disConnect()
	{
		if(!isConnected) {
			return false;
		}
		sensorLoop.interrupt(); // Kill sensor loop
		isConnected = false;
		// Wait for the sensor loop to die, 100ms should do it
        try {
            Thread.sleep(100);
        } catch (InterruptedException ex) {
        }
		try {
			hummingbird.disconnect(); // Close the hummingbird connection
		}
		catch (Exception e) {
			
		}
		//hummingbird = null;
		return true;
	}
		
	//  The following functions just get the sensors - they don't call Hummingbird directly, they just get the last sensor value found in the sensor loop
	public int[] getSensors() {
		if(!isConnected || sensors == null) {
			return null;
		}
		else {
			return sensors;
		}
		
	}
	
	// Returns the sensor value at port n
	public Integer getSensorValue(int port) {
		if(!isConnected || sensors == null) {
			return null;
		}
		else {
			if(port > 0 && port < 5)
				return sensors[port-1];
			else
				return null;
		}
	}
	
	// Returns the sensor value at port n as a temperature in Celcius
	public Double getTemperatureAtPort(int port) {
		if(!isConnected || sensors == null) {
			return null;
		}
		else {
			double temperature;
			if(port > 0 && port < 5) {
				temperature = Math.floor(((sensors[port-1]-127)/2.4+25)*100/100);
				return temperature;	
			}
			else
				return null;
		}
	}
	
	/* Returns the sensor value at port n as a distance in cm
	 * Did a fifth order polynomial regression at http://arachnoid.com/polysolve/index.html
	 * Used the following (measured) data:
	 * ADC reading  distance (cm)
	 *			21	80
				23	70
				27	60
				32	50
				37	40
				47	30
				69	20
				93	15
				138	10
				158	8
	* Got the following equation:
	* distance = 206.76903754529479-9.3402257299483011*reading +  0.19133513242939543*reading^2 
                -0.0019720997497951645*reading^3 + 9.9382154479167215*(10^-6)*reading^4
  				-1.9442731496914311*(10^-8)*reading^5;
  	*/
	
	public Integer getDistanceAtPort(int port) {
		if(!isConnected || sensors == null) {
			return null;
		}
		else {
			double distance;
			double reading;
			if(port > 0 && port < 5) {
				reading = sensors[port-1];
				// Cap the maximum sensor value to 80 cm
				if(reading < 23)
				{
					distance = 80;
				}
				else {
					distance = 206.76903754529479-9.3402257299483011*reading + 0.19133513242939543*Math.pow(reading, 2) 
		                - 0.0019720997497951645*Math.pow(reading, 3)  + 9.9382154479167215*Math.pow(10,-6)*Math.pow(reading, 4) 
		  				- 1.9442731496914311*Math.pow(10, -8)*Math.pow(reading, 5) ;
				}
				return (int)distance;	
			}
			else
				return null;
		}
	}
	
	
	/* Parses the Hummingbird output string and sets it
	   * out/motor/port/speed (port 1 or 2, speed -100 to 100)
	   * out/servo/port/position (port 1 to 4, position 0 to 160)
	   * out/vibration/port/speed (port 1 or 2, speed 0 to 100)
	   * out/led/port/intensity (port 1 to 4, intensity 0 to 100)
	   * out/triled/port/R/G/B (port 1 or 2, R, G, B are intensities 0 to 100)
	*/
	public boolean setOutput(String setter)
	{
		if(!isConnected) {
			return false;
		}
		else
		{
			// Sets LED, arguments are 0 to 100
			// out/led/port/intensity
			if(setter.substring(0,3).equals("led")) {
				int port= 0;
				int intensity = 0;
				int lastSlash = setter.lastIndexOf('/');
				try {
					port = (int)(Double.parseDouble(setter.substring(4,lastSlash)));
					intensity = (int)(Double.parseDouble(setter.substring(lastSlash+1)));
				}
				// You've just sent a non-number, so the "set" did not work 
				catch(NumberFormatException e) {
					return false;
				}
				if(port > 0 && port < 5) 
				{
					if(intensity > 100)
						intensity = 100;
					if(intensity < 0)
						intensity = 0;
					hummingbird.setLED(port-1, (int)(intensity*2.55));
					return true;
				}
			}
			// Sets motor, arguments are -100 to 100
			// out/motor/port/speed
			else if(setter.substring(0,5).equals("motor")) {
				int port= 0;
				int speed = 0;
				int lastSlash = setter.lastIndexOf('/');
				try {
					// in case someone sends "1.0" to port
					port = (int)(Double.parseDouble(setter.substring(6,lastSlash)));
					// Handle fractional speeds
					speed = (int)(Double.parseDouble(setter.substring(lastSlash+1)));
				}
				// You've just sent a non-number, so the "set" did not work 
				catch(NumberFormatException e) {
					return false;
				}
				if(port == 1 || port == 2) 
				{
					if(speed > 100)
						speed = 100;
					if(speed < -100)
						speed = -100;
					hummingbird.setMotorVelocity(port-1, (int)(speed*2.55));
					return true;
				}
			}
			// Sets servo, arguments are 0 to 160
			// out/servo/port/position
			else if(setter.substring(0,5).equals("servo")) {
				int port= 0;
				int position = 0;
				int lastSlash = setter.lastIndexOf('/');
				try {
					port = (int)(Double.parseDouble(setter.substring(6,lastSlash)));
					position = (int)(Double.parseDouble(setter.substring(lastSlash+1)));
				}
				// You've just sent a non-number, so the "set" did not work 
				catch(NumberFormatException e) {
					return false;
				}
				if(port > 0 && port < 5) 
				{
					if(position > 180)
						position = 180;
					if(position < 0)
						position = 0;
					hummingbird.setServoPosition(port-1, (int)(position*215/180));
					return true;
				}
			}
			// Sets Tri-color LED, arguments are 0 to 100 for R, G, B
			// out/triled/port/red/green/blue
			else if(setter.substring(0,6).equals("triled")) {
				int port= 0;
				int redLED = 0;
				int greenLED = 0;
				int blueLED = 0;
				int [] slashes = new int[3];
				slashes[0] = setter.indexOf('/', 7);
				slashes[1] = setter.indexOf('/', slashes[0]+1);
				slashes[2] = setter.lastIndexOf('/');
				
				try {
					port = (int)(Double.parseDouble(setter.substring(7,slashes[0])));
					redLED = (int)(Double.parseDouble(setter.substring(slashes[0]+1, slashes[1])));
					greenLED = (int)(Double.parseDouble(setter.substring(slashes[1]+1, slashes[2])));
					blueLED = (int)(Double.parseDouble(setter.substring(slashes[2]+1)));
				}
				// You've just sent a non-number, so the "set" did not work 
				catch(NumberFormatException e) {
					return false;
				}
				if(port == 1 || port == 2) 
				{
					if(redLED > 100)
						redLED = 100;
					if(redLED < 0)
						redLED = 0;
					
					if(greenLED > 100)
						greenLED = 100;
					if(greenLED < 0)
						greenLED = 0;
					
					if(blueLED > 100)
						blueLED = 100;
					if(blueLED < 0)
						blueLED = 0;
					
					hummingbird.setFullColorLED(port-1, redLED, greenLED, blueLED);
					return true;
				}
			}
			// Sets vibration motor, arguments are 0 to 100
			// out/vibration/port/intensity
			else if(setter.substring(0,9).equals("vibration")) {
				int port= 0;
				int intensity = 0;
				int lastSlash = setter.lastIndexOf('/');
				
				try {
					port = (int)(Double.parseDouble(setter.substring(10,lastSlash)));
					intensity = (int)(Double.parseDouble(setter.substring(lastSlash+1)));
				}
				// You've just sent a non-number, so the "set" did not work 
				catch(NumberFormatException e) {
					return false;
				}
				
				if(port == 1 || port == 2) 
				{
					if(intensity > 100)
						intensity = 100;
					if(intensity < 0)
						intensity = 0;
					hummingbird.setVibrationMotorSpeed(port-1, (int)(intensity*2.55));
					return true;
				}
			}
			
			return false;
		}
		
	}
	
}
