package io.github.s5uishida.iot.device.ppd42ns.driver;

/*
 * @author s5uishida
 *
 */
public interface IPPD42NSHandler {
	void handle(String pinName, PPD42NSObservationData data);
}
