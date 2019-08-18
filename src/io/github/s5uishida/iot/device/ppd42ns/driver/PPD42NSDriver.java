package io.github.s5uishida.iot.device.ppd42ns.driver;

import java.util.Date;
import java.util.concurrent.BlockingQueue;
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

	private final Pin GPIO_IN = RaspiPin.GPIO_14;
	private final int OBSERVE_TIMEOUT_MILLIS = 30000;
	private final int GPIO_IN_TIMEOUT_MILLIS = 50000;

	private final GpioController gpio;

	private GpioPinDigitalInput diPin;
	private String logPrefix;

	private final AtomicInteger useCount = new AtomicInteger(0);
	private final BlockingQueue<PPD42NSObservationData> queue = new LinkedBlockingQueue<PPD42NSObservationData>();

	private static PPD42NSDriver ppd42ns;

	private PPD42NSGpioPinListenerDigital ppd42nsListener;

	synchronized public static PPD42NSDriver getInstance() {
		if (ppd42ns == null) {
			ppd42ns = new PPD42NSDriver();
		}
		return ppd42ns;
	}

	private PPD42NSDriver() {
		GpioFactory.setDefaultProvider(new RaspiGpioProvider(RaspiPinNumberingScheme.BROADCOM_PIN_NUMBERING));
		gpio = GpioFactory.getInstance();
		logPrefix = "[" + getName() + "] ";
	}

	synchronized public void open() {
		try {
			LOG.debug(logPrefix + "before - useCount:{}", useCount.get());
			if (useCount.compareAndSet(0, 1)) {
				diPin = gpio.provisionDigitalInputPin(GPIO_IN, PinPullResistance.PULL_DOWN);
				diPin.setShutdownOptions(true);
				ppd42nsListener = new PPD42NSGpioPinListenerDigital(this, queue);
				diPin.addListener(ppd42nsListener);
				LOG.info(logPrefix + "opened SPI SLCK.");
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
				gpio.shutdown();
				LOG.info(logPrefix + "closed SPI SLCK.");
			}
		} finally {
			LOG.debug(logPrefix + "after - useCount:{}", useCount.get());
		}
	}

	public String getName() {
		return GPIO_IN.getName().replaceAll("\\s", "_");
	}

	public String getLogPrefix() {
		return logPrefix;
	}

	public PPD42NSObservationData read() {
		queue.clear();
		ppd42nsListener.start();
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
		PPD42NSDriver ppd42ns = PPD42NSDriver.getInstance();
		ppd42ns.open();

		while (true) {
			PPD42NSObservationData data = ppd42ns.read();
			LOG.info(data.toString());
		}

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
				queue.offer(data);
				LOG.trace(ppd42ns.getLogPrefix() + "offer - {}", data.toString());
				reset();
			}
		}
	}
}
