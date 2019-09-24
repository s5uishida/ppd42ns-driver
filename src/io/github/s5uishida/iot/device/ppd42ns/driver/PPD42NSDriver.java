package io.github.s5uishida.iot.device.ppd42ns.driver;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiGpioProvider;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.RaspiPinNumberingScheme;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;

/*
 * Refer to http://wiki.seeedstudio.com/Grove-Dust_Sensor/
 *
 * @author s5uishida
 *
 */
public class PPD42NSDriver {
	private static final Logger LOG = LoggerFactory.getLogger(PPD42NSDriver.class);

	private final int OBSERVE_TIMEOUT_MILLIS = 30000;
	private final int GPIO_IN_TIMEOUT_MILLIS = 50000;

	private final Pin gpioPin;
	private final GpioController gpio;
	private final IPPD42NSHandler ppd42nsHandler;
	private final String logPrefix;

	private GpioPinDigitalInput diPin;

	private static final ConcurrentHashMap<String, PPD42NSDriver> map = new ConcurrentHashMap<String, PPD42NSDriver>();

	private final AtomicInteger useCount = new AtomicInteger(0);
	private final BlockingQueue<PPD42NSObservationData> queue = new LinkedBlockingQueue<PPD42NSObservationData>();

	private PPD42NSGpioPinListenerDigital ppd42nsListener;

	synchronized public static PPD42NSDriver getInstance() {
		return getInstance(RaspiPin.GPIO_10, null);
	}

	synchronized public static PPD42NSDriver getInstance(Pin gpioPin) {
		return getInstance(gpioPin, null);
	}

	synchronized public static PPD42NSDriver getInstance(Pin gpioPin, IPPD42NSHandler ppd42nsHandler) {
		String key = getName(Objects.requireNonNull(gpioPin));
		PPD42NSDriver ppd42ns = map.get(key);
		if (ppd42ns == null) {
			ppd42ns = new PPD42NSDriver(gpioPin, ppd42nsHandler);
			map.put(key, ppd42ns);
		}
		return ppd42ns;
	}

	private PPD42NSDriver(Pin gpioPin, IPPD42NSHandler ppd42nsHandler) {
		if (gpioPin.equals(RaspiPin.GPIO_10) || gpioPin.equals(RaspiPin.GPIO_20) || gpioPin.equals(RaspiPin.GPIO_14)) {
			this.gpioPin = gpioPin;
		} else {
			throw new IllegalArgumentException("The set " + getName(gpioPin) + " is not " +
					getName(RaspiPin.GPIO_10) + ", " +
					getName(RaspiPin.GPIO_20) + " or " +
					getName(RaspiPin.GPIO_14) + ".");
		}
		logPrefix = "[" + getName() + "] ";
		GpioFactory.setDefaultProvider(new RaspiGpioProvider(RaspiPinNumberingScheme.BROADCOM_PIN_NUMBERING));
		gpio = GpioFactory.getInstance();
		this.ppd42nsHandler = ppd42nsHandler;
	}

	synchronized public void open() {
		try {
			LOG.debug(logPrefix + "before - useCount:{}", useCount.get());
			if (useCount.compareAndSet(0, 1)) {
				diPin = gpio.provisionDigitalInputPin(gpioPin, PinPullResistance.PULL_DOWN);
				diPin.setShutdownOptions(true);
				ppd42nsListener = new PPD42NSGpioPinListenerDigital(this, queue);
				diPin.addListener(ppd42nsListener);
				if (ppd42nsHandler != null) {
					start();
				}
				LOG.info(logPrefix + "opened");
			}
		} finally {
			LOG.debug(logPrefix + "after - useCount:{}", useCount.get());
		}
	}

	synchronized public void close() {
		try {
			LOG.debug(logPrefix + "before - useCount:{}", useCount.get());
			if (useCount.compareAndSet(1, 0)) {
				diPin.removeAllListeners();
				gpio.unprovisionPin(diPin);
//				gpio.shutdown();
				queue.clear();
				LOG.info(logPrefix + "closed");
			}
		} finally {
			LOG.debug(logPrefix + "after - useCount:{}", useCount.get());
		}
	}

	public static String getName(Pin gpioPin) {
		return gpioPin.getName().replaceAll("\\s", "_");
	}

	public String getName() {
		return gpioPin.getName().replaceAll("\\s", "_");
	}

	public String getLogPrefix() {
		return logPrefix;
	}

	private void start() {
		queue.clear();
		ppd42nsListener.start();
	}

	public PPD42NSObservationData read() {
		if (ppd42nsHandler != null) {
			LOG.warn("read() is currently not available.");
			return null;
		}

		start();

		try {
			return queue.poll(GPIO_IN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			LOG.warn("caught - {}", e.toString());
			return null;
		}
	}

	/******************************************************************************************************************
	 * Sample main
	 ******************************************************************************************************************/
	public static void main(String[] args) {
		testRead();
	}

	public static void testRead() {
		PPD42NSDriver ppd42ns = PPD42NSDriver.getInstance(RaspiPin.GPIO_10);
		ppd42ns.open();

		while (true) {
			PPD42NSObservationData data = ppd42ns.read();
			LOG.info("[{}] {}", ppd42ns.getName(), data.toString());
		}

//		if (ppd42ns != null) {
//			ppd42ns.close();
//		}
	}

	public static void testHandler() {
		PPD42NSDriver ppd42ns = PPD42NSDriver.getInstance(RaspiPin.GPIO_10, new MyPPD42NSHandler());
		ppd42ns.open();

//		if (ppd42ns != null) {
//			ppd42ns.close();
//		}
	}

	class PPD42NSGpioPinListenerDigital implements GpioPinListenerDigital {
		private final Logger LOG = LoggerFactory.getLogger(PPD42NSGpioPinListenerDigital.class);

		private final PPD42NSDriver ppd42ns;
		private final BlockingQueue<PPD42NSObservationData> queue;

		private Date startTime;
		private long startLowTimeMillis;
		private long totalLowTimeMillis;

		public PPD42NSGpioPinListenerDigital(PPD42NSDriver ppd42ns, BlockingQueue<PPD42NSObservationData> queue) {
			this.ppd42ns = ppd42ns;
			this.queue = queue;
		}

		public void reset() {
			startTime = null;
			startLowTimeMillis = -1;
			totalLowTimeMillis = 0;
		}

		public void start() {
			reset();
			startTime = new Date();
		}

		private float pcs2ugm3(float pcs) {
			double density = 1.65 * Math.pow(10, 12);
			double r25 = 0.44 * Math.pow(10, -6);
			double vol25 = (4 / 3) * Math.PI * Math.pow(r25, 3);
			double mass25 = density * vol25;
			double K = 3531.5;
			return (float)(pcs * K * mass25);
		}

		@Override
		public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
			LOG.trace(ppd42ns.getLogPrefix() + "{} -> {}", event.getPin(), event.getState());

			if (startTime == null) {
				return;
			}

			Date currentTime = new Date();
			if (event.getState() == PinState.LOW) {
				startLowTimeMillis = currentTime.getTime();
				return;
			} else if (event.getState() == PinState.HIGH && startLowTimeMillis > 0) {
				totalLowTimeMillis += currentTime.getTime() - startLowTimeMillis;
			} else {
				return;
			}

			if ((currentTime.getTime() - startTime.getTime()) >= OBSERVE_TIMEOUT_MILLIS) {
				float ratio = 100f * ((float)totalLowTimeMillis) / ((float)(currentTime.getTime() - startTime.getTime()));
				float pcs = (float)(1.1 * Math.pow(ratio, 3) - 3.8 * Math.pow(ratio, 2) + 520.0 * ratio + 0.62);
				float ugm3 = pcs2ugm3(pcs);
				PPD42NSObservationData data = new PPD42NSObservationData(startTime, currentTime, pcs, ugm3);
				if (ppd42ns.ppd42nsHandler != null) {
					LOG.trace(ppd42ns.getLogPrefix() + "handle - {}", data.toString());
					ppd42ns.ppd42nsHandler.handle(ppd42ns.getName(), data);
					start();
				} else {
					LOG.trace(ppd42ns.getLogPrefix() + "offer - {}", data.toString());
					queue.offer(data);
					reset();
				}
			}
		}
	}
}

/******************************************************************************************************************
 * Sample implementation of IPPD42NSHandler interface
 ******************************************************************************************************************/
class MyPPD42NSHandler implements IPPD42NSHandler {
	private static final Logger LOG = LoggerFactory.getLogger(MyPPD42NSHandler.class);

	@Override
	public void handle(String pinName, PPD42NSObservationData data) {
		LOG.info("[{}] {}", pinName, data.toString());
	}
}
